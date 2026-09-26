package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;

public class SpawnerToggleManager implements Listener {

    private final DimensionToggle plugin;
    private boolean netherEnabled;

    public SpawnerToggleManager(DimensionToggle plugin) {
        this.plugin = plugin;
        this.netherEnabled = plugin.getConfigManager().getData().getBoolean("nether.spawners-enabled", true);
    }

    public boolean isEnabled(ToggleDimension dimension) {
        return dimension == ToggleDimension.NETHER ? netherEnabled : true;
    }

    public void setEnabled(ToggleDimension dimension, boolean value) {
        if (dimension != ToggleDimension.NETHER) {
            return;
        }
        this.netherEnabled = value;
        plugin.getConfigManager().getData().set("nether.spawners-enabled", value);
        plugin.getConfigManager().saveData();
    }

    public boolean toggle(ToggleDimension dimension) {
        boolean newValue = !isEnabled(dimension);
        setEnabled(dimension, newValue);
        return newValue;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        // Fires for every spawner spawn server-wide (mob farms!) - check the
        // flag first so the default "enabled" case costs one boolean read.
        if (netherEnabled) {
            return;
        }
        if (event.getLocation().getWorld().getEnvironment() == World.Environment.NETHER) {
            event.setCancelled(true);
        }
    }
}
