package net.dimensiontoggle.config;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import net.dimensiontoggle.model.WorldScope;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class GuiConfigManager {

    private final DimensionToggle plugin;

    private FileConfiguration mainConfig;
    private FileConfiguration netherConfig;
    private FileConfiguration endConfig;
    private FileConfiguration overworldConfig;

    public GuiConfigManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        mainConfig = YamlConfiguration.loadConfiguration(load("gui/main/config.yml"));
        netherConfig = YamlConfiguration.loadConfiguration(load("gui/nether/config.yml"));
        endConfig = YamlConfiguration.loadConfiguration(load("gui/end/config.yml"));
        overworldConfig = YamlConfiguration.loadConfiguration(load("gui/overworld/config.yml"));
    }

    public void reload() {
        loadAll();
    }

    private File load(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        plugin.getConfigManager().getSynchronizer().sync(file, resourcePath);
        return file;
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

    public FileConfiguration getOverworld() {
        return overworldConfig;
    }

    public FileConfiguration getFor(WorldScope scope) {
        return switch (scope) {
            case OVERWORLD -> overworldConfig;
            case NETHER -> netherConfig;
            case END -> endConfig;
        };
    }

    public FileConfiguration getFor(ToggleDimension dimension) {
        return dimension == ToggleDimension.NETHER ? netherConfig : endConfig;
    }
}
