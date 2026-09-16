package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class PvpIntegrationManager {

    private final DimensionToggle plugin;

    public PvpIntegrationManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("PvPManager") != null;
    }

    public boolean isPvpEnabled(ToggleDimension dimension) {
        World world = dimension.findWorld();
        return world != null && world.getPVP();
    }

    public boolean toggle(ToggleDimension dimension) {
        boolean newValue = !isPvpEnabled(dimension);
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == dimension.getEnvironment()) {
                world.setPVP(newValue);
            }
        }
        return newValue;
    }
}
