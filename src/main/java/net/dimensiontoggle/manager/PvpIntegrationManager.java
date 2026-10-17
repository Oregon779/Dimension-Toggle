package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class PvpIntegrationManager implements Listener {

    private final DimensionToggle plugin;

    public PvpIntegrationManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("PvPManager") != null;
    }

    public boolean isPvpEnabled(ToggleDimension dimension) {
        World world = dimension.findWorld();
        if (world == null) {
            return false;
        }
        Boolean value = world.getGameRuleValue(GameRules.PVP);
        return value == null || value;
    }

    // PvP is a gamerule since Minecraft 26, so the world saves it itself.
    public boolean toggle(ToggleDimension dimension) {
        boolean newValue = !isPvpEnabled(dimension);
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == dimension.getEnvironment()) {
                world.setGameRule(GameRules.PVP, newValue);
            }
        }
        return newValue;
    }

    // Older versions kept the PvP choice in data.yml because World#setPVP
    // wasn't saved with the world. Carry such a value over into the gamerule
    // once, then drop it - from here on the world owns the setting.
    public void applyStoredState() {
        for (World world : Bukkit.getWorlds()) {
            migrate(world);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        migrate(event.getWorld());
    }

    private void migrate(World world) {
        ToggleDimension dimension = ToggleDimension.fromEnvironment(world.getEnvironment());
        if (dimension == null) {
            return;
        }
        String path = "pvp." + dimension.getKey();
        var data = plugin.getConfigManager().getData();
        if (!data.isBoolean(path)) {
            return;
        }
        // Like the old toggle: every loaded world of this dimension type.
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.getEnvironment() == world.getEnvironment()) {
                candidate.setGameRule(GameRules.PVP, data.getBoolean(path));
            }
        }
        data.set(path, null);
        if (data.getConfigurationSection("pvp") != null && data.getConfigurationSection("pvp").getKeys(false).isEmpty()) {
            data.set("pvp", null);
        }
        plugin.getConfigManager().saveData();
    }
}
