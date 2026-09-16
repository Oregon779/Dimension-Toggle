package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;

public class KeepInventoryManager {

    private final DimensionToggle plugin;

    public KeepInventoryManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled(ToggleDimension dimension) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == dimension.getEnvironment()) {
                Boolean value = world.getGameRuleValue(GameRule.KEEP_INVENTORY);
                return value != null && value;
            }
        }
        return false;
    }

    public void setEnabled(ToggleDimension dimension, boolean enabled) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == dimension.getEnvironment()) {
                world.setGameRule(GameRule.KEEP_INVENTORY, enabled);
            }
        }
    }

    public boolean toggle(ToggleDimension dimension) {
        boolean newValue = !isEnabled(dimension);
        setEnabled(dimension, newValue);
        return newValue;
    }
}
