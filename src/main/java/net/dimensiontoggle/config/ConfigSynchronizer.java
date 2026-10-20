package net.dimensiontoggle.config;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.io.AtomicFiles;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

// Keeps every YAML file the plugin ships (config.yml, languages/*/messages.yml,
// gui/*/config.yml) up to date across plugin updates. Runs right before such
// a file is read, on enable and on /dt reload:
//
//   1. missing file      -> copy the default, remember all its options
//   2. versioned migrations for renamed/removed options (ConfigMigrations)
//   3. options that are NEW in this plugin version are added at their place,
//      with comments and default values (ConfigUpdater.merge)
//   4. before anything is written, the old file is backed up
//
// "New" means: not in the set of options this plugin knew for that file the
// last time it ran (kept in config-state.yml). So an option the admin deleted
// on purpose is not added back - only genuinely new options are. On the very
// first run with this system there is no such set yet; then everything
// missing is added once, because older versions couldn't add nested options.
public class ConfigSynchronizer {

    static final String STATE_FILE = "config-state.yml";
    static final String BACKUP_FOLDER = "backups";
    static final int BACKUPS_KEPT = 5;
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final DimensionToggle plugin;
    private final File stateFile;
    private YamlConfiguration state;

    public ConfigSynchronizer(DimensionToggle plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), STATE_FILE);
    }

    // Re-read config-state.yml on the next sync (start of every load/reload),
    // so deleting or editing it takes effect with /dt reload.
    public void reloadState() {
        state = null;
    }

    public void sync(File userFile, String resourcePath) {
        List<String> defaultLines = readResource(resourcePath);
        if (defaultLines == null) {
            return; // nothing shipped to compare with (e.g. a custom language)
        }
        List<String> defaultPaths;
        try {
            defaultPaths = ConfigUpdater.paths(defaultLines);
        } catch (InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "The built-in " + resourcePath + " is not valid YAML", e);
            return;
        }

        if (!userFile.exists()) {
            plugin.saveResource(resourcePath, false);
            remember(resourcePath, defaultPaths, ConfigMigrations.latestVersion(resourcePath));
            return;
        }

        try {
            List<String> original = Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
            List<String> lines = original;
            List<String> notes = new ArrayList<>();

            int version = state().getInt(id(resourcePath) + ".version", 0);
            for (ConfigMigrations.Migration migration : ConfigMigrations.pending(resourcePath, version)) {
                lines = migrate(lines, defaultLines, migration, notes);
            }

            List<String> eligible = null;
            if (state().contains(id(resourcePath) + ".known")) {
                Set<String> known = new HashSet<>(state().getStringList(id(resourcePath) + ".known"));
                eligible = defaultPaths.stream().filter(p -> !known.contains(ConfigUpdater.display(p))).toList();
            }
            ConfigUpdater.Result result = ConfigUpdater.merge(lines, defaultLines, eligible);
            lines = result.lines();

            if (!lines.equals(original)) {
                Path backup = backup(userFile, resourcePath, original);
                AtomicFiles.write(userFile.toPath(), lines);
                if (result.changed()) {
                    notes.add(result.added().size() + " new option(s) added: " + summary(result.added()));
                }
                plugin.getLogger().info(resourcePath + " updated - " + String.join("; ", notes)
                        + ". Your own settings were kept. Backup: " + relative(backup));
            }
            for (String skipped : result.skipped()) {
                plugin.getLogger().warning(resourcePath + ": could not add '" + skipped
                        + "' automatically - please add it by hand (see the default file in the plugin jar).");
            }
            // Never step back: a version number used by a dropped dev-build
            // migration must not make a later migration run twice.
            remember(resourcePath, defaultPaths, Math.max(version, ConfigMigrations.latestVersion(resourcePath)));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not update " + resourcePath + " automatically", e);
        }
    }

    private List<String> migrate(List<String> lines, List<String> defaultLines, ConfigMigrations.Migration migration,
                                 List<String> notes) {
        List<String> current = lines;
        int applied = 0;
        for (ConfigMigrations.Step step : migration.steps()) {
            List<String> next = switch (step) {
                case ConfigMigrations.Rename rename -> ConfigUpdater.rename(current, rename.oldPath(), rename.newPath());
                case ConfigMigrations.Remove remove -> ConfigUpdater.remove(current, remove.path());
                case ConfigMigrations.Reset ignored -> new ArrayList<>(defaultLines);
            };
            if (next != null) {
                current = next;
                applied++;
            }
        }
        if (applied > 0) {
            notes.add("migration " + migration.version() + " (" + migration.description() + ")");
        }
        return current;
    }

    private static String summary(List<String> paths) {
        List<String> shown = paths.stream().limit(10).map(ConfigUpdater::display).toList();
        return String.join(", ", shown) + (paths.size() > 10 ? ", ..." : "");
    }

    private String relative(Path path) {
        return plugin.getDataFolder().toPath().relativize(path).toString().replace('\\', '/');
    }

    // -------------------------------------------------------------------
    //   Backups
    // -------------------------------------------------------------------

    private Path backup(File userFile, String resourcePath, List<String> original) throws IOException {
        Path folder = plugin.getDataFolder().toPath().resolve(BACKUP_FOLDER);
        Files.createDirectories(folder);
        String prefix = backupPrefix(resourcePath);
        Path target = folder.resolve(prefix + LocalDateTime.now().format(BACKUP_TIME) + ".yml");
        int n = 1;
        while (Files.exists(target)) {
            target = folder.resolve(prefix + LocalDateTime.now().format(BACKUP_TIME) + "-" + n++ + ".yml");
        }
        AtomicFiles.write(target, original);
        pruneBackups(folder, prefix);
        return target;
    }

    // "gui/nether/config.yml" -> "gui-nether-config-"
    static String backupPrefix(String resourcePath) {
        String base = resourcePath.endsWith(".yml") ? resourcePath.substring(0, resourcePath.length() - 4) : resourcePath;
        return base.replace('/', '-').replace('\\', '-') + "-";
    }

    private static void pruneBackups(Path folder, String prefix) throws IOException {
        File[] files = folder.toFile().listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".yml"));
        if (files == null || files.length <= BACKUPS_KEPT) {
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (int i = 0; i < files.length - BACKUPS_KEPT; i++) {
            Files.deleteIfExists(files[i].toPath());
        }
    }

    // -------------------------------------------------------------------
    //   State: which options of which file this plugin already knew
    // -------------------------------------------------------------------

    private YamlConfiguration state() {
        if (state == null) {
            state = new YamlConfiguration();
            if (stateFile.exists()) {
                try {
                    state.load(stateFile);
                } catch (IOException | InvalidConfigurationException e) {
                    plugin.getLogger().warning(STATE_FILE + " could not be read (" + e.getMessage()
                            + ") - all missing options will be added once.");
                    state = new YamlConfiguration();
                }
            }
        }
        return state;
    }

    private void remember(String resourcePath, List<String> paths, int version) {
        String id = id(resourcePath);
        state().set(id + ".version", version);
        state().set(id + ".known", paths.stream().map(ConfigUpdater::display).toList());
        state().options().setHeader(List.of(
                "Managed by DimensionToggle - do not edit.",
                "Remembers which options of each config file the plugin already knew,",
                "so plugin updates only add options that are really new.",
                "Delete this file to have all missing options added again on the next start."));
        try {
            plugin.getDataFolder().mkdirs();
            AtomicFiles.write(stateFile.toPath(), state().saveToString());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + STATE_FILE, e);
        }
    }

    // "languages/en/messages.yml" -> "languages_en_messages_yml" (a single key)
    static String id(String resourcePath) {
        return "files." + resourcePath.replaceAll("[^A-Za-z0-9]", "_");
    }

    private List<String> readResource(String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return null;
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (IOException e) {
            return null;
        }
    }
}
