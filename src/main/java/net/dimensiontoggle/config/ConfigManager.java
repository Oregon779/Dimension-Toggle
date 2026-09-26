package net.dimensiontoggle.config;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.io.AtomicFiles;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class ConfigManager {

    private static final String UPDATE_HEADER = "# ===================================================================\n"
            + "# NEU seit dem letzten Update automatisch hinzugefuegt.\n"
            + "# Deine bisherigen Einstellungen weiter oben in dieser Datei wurden\n"
            + "# dabei NICHT veraendert.\n"
            + "# ===================================================================";

    private static final String[] SUPPORTED_LANGUAGES = {"en", "de"};
    private static final String DEFAULT_LANGUAGE = "en";

    private static final String[] NESTED_PATHS_TO_CHECK = {
            "schedule.nether.countdown-enabled",
            "schedule.end.countdown-enabled"
    };

    private final DimensionToggle plugin;

    private File configFile;
    private FileConfiguration config;

    private File languagesFolder;
    private String activeLanguage;
    private FileConfiguration messages;

    private File dataFile;
    private FileConfiguration data;
    // Latest serialized data.yml waiting to be written, or null if nothing is
    // pending. Lets a burst of saves (lockdown saves 3x in one tick) collapse
    // into a single disk write.
    private final AtomicReference<String> pendingData = new AtomicReference<>();

    public ConfigManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        loadConfig();
        loadMessages();
        loadData();
    }

    public void reloadAll() {
        // A GUI click or state change may still have a write queued for
        // config.yml/data.yml - reading before it lands would load stale data.
        plugin.getIoExecutor().drain(5000);
        loadAll();
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        boolean isNew = !configFile.exists();
        if (isNew) {
            plugin.saveResource("config.yml", false);
        } else {
            mergeWithDefault(configFile, "config.yml", true);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    @FunctionalInterface
    public interface ConfigEdit {
        boolean apply(java.nio.file.Path configFile) throws IOException;
    }

    // Callers update the in-memory config themselves (so the change takes
    // effect immediately); this persists it to config.yml on the IO thread,
    // in submission order, without rewriting the user's comments/layout.
    public void persistConfigEdit(String what, ConfigEdit edit) {
        java.nio.file.Path path = configFile.toPath();
        plugin.getIoExecutor().execute(() -> {
            try {
                if (!edit.apply(path)) {
                    plugin.getLogger().warning("Could not persist " + what + ": not found in config.yml in the "
                            + "expected format. The change is only active until the next reload/restart.");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not persist " + what + ": " + e.getMessage());
            }
        });
    }

    public File getConfigFile() {
        return configFile;
    }

    private void loadMessages() {
        languagesFolder = new File(plugin.getDataFolder(), "languages");

        migrateOldSingleMessagesFile();

        for (String lang : SUPPORTED_LANGUAGES) {
            ensureLanguageFile(lang);
        }

        String requested = config.getString("language", DEFAULT_LANGUAGE);
        activeLanguage = requested == null ? DEFAULT_LANGUAGE : requested.trim().toLowerCase();

        File activeFile = languageFile(activeLanguage);
        if (!activeFile.exists()) {
            plugin.getLogger().warning("Language '" + activeLanguage + "' not found (folder languages/"
                    + activeLanguage + " is missing). Falling back to default language '" + DEFAULT_LANGUAGE + "'.");
            activeLanguage = DEFAULT_LANGUAGE;
            activeFile = languageFile(activeLanguage);
        }

        messages = YamlConfiguration.loadConfiguration(activeFile);
    }

    private void migrateOldSingleMessagesFile() {
        File oldFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!oldFile.exists() || languagesFolder.exists()) {
            return;
        }

        String detectedLanguage = detectLanguage(oldFile);
        File target = languageFile(detectedLanguage);

        try {
            target.getParentFile().mkdirs();
            java.nio.file.Files.copy(oldFile.toPath(), target.toPath());
            File renamedOld = new File(plugin.getDataFolder(), "messages.yml.old");
            oldFile.renameTo(renamedOld);

            plugin.getLogger().info("Existing messages.yml was picked up as language '" + detectedLanguage
                    + "' and moved to languages/" + detectedLanguage + "/messages.yml "
                    + "(your existing texts are preserved; the original file also remains as messages.yml.old).");

            if (detectedLanguage.equals("de")) {
                plugin.getLogger().info("To actually use these carried-over German texts, "
                        + "set in config.yml: language: \"de\"");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not automatically carry over the old messages.yml. "
                    + "Please copy it manually to languages/de/messages.yml or languages/en/messages.yml.", e);
        }
    }

    private String detectLanguage(File file) {
        try {
            String content = java.nio.file.Files
                    .readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8).toLowerCase();
            int hits = 0;
            for (String marker : new String[]{"nutze", "aktiviert", "deaktiviert", "spieler", "wartung", "für", "gültig", "aufgehoben"}) {
                if (content.contains(marker)) {
                    hits++;
                }
            }
            return hits >= 2 ? "de" : DEFAULT_LANGUAGE;
        } catch (IOException e) {
            return DEFAULT_LANGUAGE;
        }
    }

    private void ensureLanguageFile(String lang) {
        File file = languageFile(lang);
        String resourcePath = "languages/" + lang + "/messages.yml";
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        } else {
            mergeWithDefault(file, resourcePath, false);
        }
    }

    private File languageFile(String lang) {
        return new File(languagesFolder, lang + "/messages.yml");
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public String getActiveLanguage() {
        return activeLanguage;
    }

    private void mergeWithDefault(File userFile, String resourcePath, boolean checkNestedPaths) {
        try {
            boolean changed = false;
            try (InputStream defaultResource = plugin.getResource(resourcePath)) {
                if (defaultResource == null) {
                    return;
                }
                changed = ConfigUpdater.update(userFile.toPath(), defaultResource, UPDATE_HEADER);
            }

            if (checkNestedPaths) {
                for (String path : NESTED_PATHS_TO_CHECK) {
                    try (InputStream defaultResource = plugin.getResource(resourcePath)) {
                        if (defaultResource != null
                                && ConfigUpdater.ensureNestedPath(userFile.toPath(), defaultResource, path)) {
                            changed = true;
                        }
                    }
                }
            }

            if (changed) {
                plugin.getLogger().info(resourcePath + " was extended with new options from the update "
                        + "(existing settings were left unchanged).");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not automatically update " + resourcePath, e);
        }
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create data.yml", e);
            }
        }
        data = loadDataFile();

        boolean changed = false;
        if (!data.isSet("dimensions.nether.enabled")) {
            data.set("dimensions.nether.enabled", config.getBoolean("dimensions.nether.enabled", true));
            changed = true;
        }
        if (!data.isSet("dimensions.end.enabled")) {
            data.set("dimensions.end.enabled", config.getBoolean("dimensions.end.enabled", true));
            changed = true;
        }
        if (changed) {
            saveData();
        }
    }

    // YamlConfiguration.loadConfiguration() swallows a parse error and hands
    // back an empty config - the next save would then overwrite the damaged
    // file with defaults (re-opening disabled dimensions, dropping lockdown).
    // Move the unreadable file aside first so the admin can still recover it.
    private FileConfiguration loadDataFile() {
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(dataFile);
            return loaded;
        } catch (IOException | InvalidConfigurationException e) {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File backup = new File(dataFile.getParentFile(), "data.yml.corrupt-" + stamp);
            String reason = e.getMessage() == null
                    ? e.getClass().getSimpleName()
                    : e.getMessage().lines().findFirst().orElse(e.getClass().getSimpleName());
            try {
                Files.move(dataFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().severe("data.yml could not be read (" + reason + ") - moved it to "
                        + backup.getName() + " and started with fresh state.");
            } catch (IOException moveError) {
                plugin.getLogger().log(Level.SEVERE, "data.yml is unreadable and could not be backed up", moveError);
            }
            return new YamlConfiguration();
        }
    }

    public FileConfiguration getData() {
        return data;
    }

    // Serializes on the calling (main) thread - FileConfiguration isn't
    // thread-safe - and leaves the actual disk write to the IO thread.
    public void saveData() {
        String yaml = data.saveToString();
        if (pendingData.getAndSet(yaml) != null) {
            return; // a write is already queued and will pick up this snapshot
        }
        File target = dataFile;
        plugin.getIoExecutor().execute(() -> {
            String latest = pendingData.getAndSet(null);
            if (latest != null) {
                writeData(target, latest);
            }
        });
    }

    // For onDisable, after the IO thread has been shut down.
    public void saveDataSync() {
        pendingData.set(null);
        writeData(dataFile, data.saveToString());
    }

    private void writeData(File target, String yaml) {
        try {
            AtomicFiles.write(target.toPath(), yaml);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }
}
