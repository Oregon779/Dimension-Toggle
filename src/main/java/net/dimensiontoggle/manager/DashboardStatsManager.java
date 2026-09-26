package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardStatsManager {

    private static final long REFRESH_INTERVAL_MILLIS = 5000;

    private final DimensionToggle plugin;
    private final Map<ToggleDimension, Integer> loadedChunksCache = new EnumMap<>(ToggleDimension.class);
    private final Map<ToggleDimension, List<Map.Entry<EntityType, Integer>>> topEntitiesCache = new EnumMap<>(ToggleDimension.class);
    private long lastRefresh = 0L;

    public DashboardStatsManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Stats are computed on demand (see refreshIfStale()) instead of on a
        // fixed schedule, so a server with no dashboard GUI open never pays
        // for scanning world.getEntities() in the background.
    }

    public void stop() {
    }

    // world.getEntities() and getLoadedChunks() scale with total entities/chunks
    // in the dimension, which on a busy 250-300 player server can be
    // thousands - not something to run unconditionally forever in the
    // background. Only recompute when the dashboard is actually about to be
    // rendered, and even then at most once per REFRESH_INTERVAL_MILLIS no
    // matter how many panels are open or how often they redraw.
    public void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < REFRESH_INTERVAL_MILLIS) {
            return;
        }
        lastRefresh = now;
        refresh();
    }

    private void refresh() {
        for (ToggleDimension dimension : ToggleDimension.values()) {
            World world = dimension.findWorld();
            if (world == null) {
                continue;
            }

            // getChunkCount(): no array copy of every loaded chunk (thousands at 300 players).
            loadedChunksCache.put(dimension, world.getChunkCount());

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
