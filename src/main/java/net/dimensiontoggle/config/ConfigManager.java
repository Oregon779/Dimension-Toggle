package net.dimensiontoggle.config;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

    public ConfigManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        loadConfig();
        loadMessages();
        loadData();
    }

    public void reloadAll() {
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
        data = YamlConfiguration.loadConfiguration(dataFile);

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

    public FileConfiguration getData() {
        return data;
    }

    public void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }
}
