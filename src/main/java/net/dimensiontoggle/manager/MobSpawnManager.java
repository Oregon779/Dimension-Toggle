package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;

public class MobSpawnManager {

    private final DimensionToggle plugin;

    public MobSpawnManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled(ToggleDimension dimension) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == dimension.getEnvironment()) {
                Boolean value = world.getGameRuleValue(GameRule.DO_MOB_SPAWNING);
                return value == null || value;
            }
        }
        return true;
    }

    public void setEnabled(ToggleDimension dimension, boolean enabled) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == dimension.getEnvironment()) {
                world.setGameRule(GameRule.DO_MOB_SPAWNING, enabled);
            }
        }
    }

    public boolean toggle(ToggleDimension dimension) {
        boolean newValue = !isEnabled(dimension);
        setEnabled(dimension, newValue);
        return newValue;
    }
}
