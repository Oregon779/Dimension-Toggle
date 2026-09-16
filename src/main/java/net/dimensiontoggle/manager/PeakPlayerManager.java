package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;

import java.util.EnumMap;
import java.util.Map;

public class PeakPlayerManager {

    private final DimensionToggle plugin;
    private final Map<ToggleDimension, Integer> peaks = new EnumMap<>(ToggleDimension.class);
    private org.bukkit.scheduler.BukkitTask task;

    public PeakPlayerManager(DimensionToggle plugin) {
        this.plugin = plugin;
        for (ToggleDimension dimension : ToggleDimension.values()) {
            peaks.put(dimension, plugin.getConfigManager().getData().getInt("peak-players." + dimension.getKey(), 0));
        }
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, 20L * 10, 20L * 10);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public int getPeak(ToggleDimension dimension) {
        return peaks.getOrDefault(dimension, 0);
    }

    private void checkAll() {
        for (ToggleDimension dimension : ToggleDimension.values()) {
            int current = plugin.getDimensionManager().countPlayersInDimension(dimension);
            if (current > peaks.getOrDefault(dimension, 0)) {
                peaks.put(dimension, current);
                plugin.getConfigManager().getData().set("peak-players." + dimension.getKey(), current);
                plugin.getConfigManager().saveData();
            }
        }
    }
}
