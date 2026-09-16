package net.dimensiontoggle.config;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

public class GuiConfigManager {

    private static final String UPDATE_HEADER = "# ===================================================================\n"
            + "# NEU seit dem letzten Update automatisch hinzugefuegt.\n"
            + "# Deine bisherigen Einstellungen weiter oben in dieser Datei wurden\n"
            + "# dabei NICHT veraendert.\n"
            + "# ===================================================================";

    private final DimensionToggle plugin;

    private FileConfiguration mainConfig;
    private FileConfiguration netherConfig;
    private FileConfiguration endConfig;

    public GuiConfigManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        mainConfig = YamlConfiguration.loadConfiguration(load("gui/main/config.yml"));
        netherConfig = YamlConfiguration.loadConfiguration(load("gui/nether/config.yml"));
        endConfig = YamlConfiguration.loadConfiguration(load("gui/end/config.yml"));
    }

    public void reload() {
        loadAll();
    }

    private File load(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        } else {
            mergeWithDefault(file, resourcePath);
        }
        return file;
    }

    private void mergeWithDefault(File userFile, String resourcePath) {
        try (InputStream defaultResource = plugin.getResource(resourcePath)) {
            if (defaultResource == null) {
                return;
            }
            boolean changed = ConfigUpdater.update(userFile.toPath(), defaultResource, UPDATE_HEADER);
            if (changed) {
                plugin.getLogger().info(resourcePath + " was extended with new options from the update.");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not automatically update " + resourcePath, e);
        }
    }

    public FileConfiguration getMain() {
        return mainConfig;
    }

    public FileConfiguration getNether() {
        return netherConfig;
    }

    public FileConfiguration getEnd() {
        return endConfig;
    }

    public FileConfiguration getFor(ToggleDimension dimension) {
        return dimension == ToggleDimension.NETHER ? netherConfig : endConfig;
    }
}
