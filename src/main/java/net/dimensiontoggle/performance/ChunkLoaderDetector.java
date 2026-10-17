package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Finds chunks that are loaded *and ticking* although no player is close
// enough to cause it - chunk loaders (ender pearls, portals, /forceload,
// other plugins). On demand only, with a short cache.
public class ChunkLoaderDetector {

    private static final long CACHE_MILLIS = 10_000;

    public enum Reason { FORCE_LOADED, PLUGIN, ENDER_PEARL, UNKNOWN }

    public record LoadedChunk(String world, int x, int z, Reason reason, String detail, int entities) {
    }

    private final DimensionToggle plugin;
    private List<LoadedChunk> cached = List.of();
    private long cachedAt;

    public ChunkLoaderDetector(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public List<LoadedChunk> find() {
        if (System.currentTimeMillis() - cachedAt > CACHE_MILLIS) {
            cached = scan();
            cachedAt = System.currentTimeMillis();
        }
        return cached;
    }

    List<LoadedChunk> scan() {
        List<LoadedChunk> result = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            // Every chunk a player keeps loaded: view distance around each
            // player, +1 for the ring that is loaded but not sent yet.
            int radius = world.getViewDistance() + 1;
            Set<Long> nearPlayers = new HashSet<>();
            for (Player player : world.getPlayers()) {
                int cx = player.getLocation().getBlockX() >> 4;
                int cz = player.getLocation().getBlockZ() >> 4;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        nearPlayers.add(Chunk.getChunkKey(cx + dx, cz + dz));
                    }
                }
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                Chunk.LoadLevel level = chunk.getLoadLevel();
                if (level != Chunk.LoadLevel.TICKING && level != Chunk.LoadLevel.ENTITY_TICKING) {
                    continue;
                }
                if (nearPlayers.contains(chunk.getChunkKey())) {
                    continue;
                }
                Entity[] entities = chunk.getEntities();
                result.add(classify(world, chunk, entities));
            }
        }
        result.sort(Comparator.comparingInt(LoadedChunk::entities).reversed());
        return result;
    }

    private static LoadedChunk classify(World world, Chunk chunk, Entity[] entities) {
        if (chunk.isForceLoaded()) {
            return new LoadedChunk(world.getName(), chunk.getX(), chunk.getZ(), Reason.FORCE_LOADED, "", entities.length);
        }
        Collection<Plugin> tickets = chunk.getPluginChunkTickets();
        if (!tickets.isEmpty()) {
            List<String> names = tickets.stream().map(Plugin::getName).sorted().toList();
            return new LoadedChunk(world.getName(), chunk.getX(), chunk.getZ(), Reason.PLUGIN,
                    String.join(", ", names), entities.length);
        }
        for (Entity entity : entities) {
            if (entity instanceof EnderPearl) {
                return new LoadedChunk(world.getName(), chunk.getX(), chunk.getZ(), Reason.ENDER_PEARL, "", entities.length);
            }
        }
        return new LoadedChunk(world.getName(), chunk.getX(), chunk.getZ(), Reason.UNKNOWN, "", entities.length);
    }
}
