package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardStatsManager {

    private final DimensionToggle plugin;
    private final Map<ToggleDimension, Integer> loadedChunksCache = new EnumMap<>(ToggleDimension.class);
    private final Map<ToggleDimension, List<Map.Entry<EntityType, Integer>>> topEntitiesCache = new EnumMap<>(ToggleDimension.class);
    private BukkitTask task;

    public DashboardStatsManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void start() {
        refresh();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 100L, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void refresh() {
        for (ToggleDimension dimension : ToggleDimension.values()) {
            World world = dimension.findWorld();
            if (world == null) {
                continue;
            }

            loadedChunksCache.put(dimension, world.getLoadedChunks().length);

            Map<EntityType, Integer> counts = new HashMap<>();
            for (Entity entity : world.getEntities()) {
                counts.merge(entity.getType(), 1, Integer::sum);
            }
            List<Map.Entry<EntityType, Integer>> top5 = counts.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .limit(5)
                    .toList();
            topEntitiesCache.put(dimension, top5);
        }
    }

    public int getLoadedChunks(ToggleDimension dimension) {
        return loadedChunksCache.getOrDefault(dimension, -1);
    }

    public List<Map.Entry<EntityType, Integer>> getTopEntities(ToggleDimension dimension) {
        return topEntitiesCache.getOrDefault(dimension, List.of());
    }
}
