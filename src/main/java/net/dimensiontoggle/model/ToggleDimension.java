package net.dimensiontoggle.model;

import org.bukkit.World;

public enum ToggleDimension {

    NETHER("nether", World.Environment.NETHER),
    END("end", World.Environment.THE_END);

    private final String key;
    private final World.Environment environment;

    ToggleDimension(String key, World.Environment environment) {
        this.key = key;
        this.environment = environment;
    }

    public String getKey() {
        return key;
    }

    public World.Environment getEnvironment() {
        return environment;
    }

    public static ToggleDimension fromEnvironment(World.Environment environment) {
        for (ToggleDimension dimension : values()) {
            if (dimension.environment == environment) {
                return dimension;
            }
        }
        return null;
    }

    public static ToggleDimension fromString(String input) {
        if (input == null) {
            return null;
        }
        for (ToggleDimension dimension : values()) {
            if (dimension.key.equalsIgnoreCase(input)) {
                return dimension;
            }
        }
        return null;
    }

    public World findWorld() {
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            if (world.getEnvironment() == environment) {
                return world;
            }
        }
        return null;
    }
}
