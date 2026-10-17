package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import net.dimensiontoggle.model.WorldScope;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;

// The vanilla "spawn_mobs" gamerule per world type - saved by the world itself.
public class MobSpawnManager {

    private final DimensionToggle plugin;

    public MobSpawnManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled(ToggleDimension dimension) {
        return isEnabled(WorldScope.of(dimension));
    }

    public boolean toggle(ToggleDimension dimension) {
        return toggle(WorldScope.of(dimension));
    }

    public boolean isEnabled(WorldScope scope) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == scope.getEnvironment()) {
                Boolean value = world.getGameRuleValue(GameRules.SPAWN_MOBS);
                return value == null || value;
            }
        }
        return true;
    }

    public void setEnabled(WorldScope scope, boolean enabled) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == scope.getEnvironment()) {
                world.setGameRule(GameRules.SPAWN_MOBS, enabled);
            }
        }
    }

    public boolean toggle(WorldScope scope) {
        boolean newValue = !isEnabled(scope);
        setEnabled(scope, newValue);
        return newValue;
    }
}
