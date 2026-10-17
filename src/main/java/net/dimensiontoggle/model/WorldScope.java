package net.dimensiontoggle.model;

import org.bukkit.Bukkit;
import org.bukkit.World;

// Every world type the plugin manages *settings* for. Unlike ToggleDimension,
// this includes the Overworld - it can have mobs managed and performance
// tools applied, but it can never be disabled, locked or locked down.
public enum WorldScope {

    OVERWORLD("overworld", World.Environment.NORMAL),
    NETHER("nether", World.Environment.NETHER),
    END("end", World.Environment.THE_END);

    private final String key;
    private final World.Environment environment;

    WorldScope(String key, World.Environment environment) {
        this.key = key;
        this.environment = environment;
    }

    public String getKey() {
        return key;
    }

    public World.Environment getEnvironment() {
        return environment;
    }

    public static WorldScope of(ToggleDimension dimension) {
        return dimension == ToggleDimension.NETHER ? NETHER : END;
    }

    public static WorldScope fromEnvironment(World.Environment environment) {
        for (WorldScope scope : values()) {
            if (scope.environment == environment) {
                return scope;
            }
        }
        return null;
    }

    public World findWorld() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == environment) {
                return world;
            }
        }
        return null;
    }
}
