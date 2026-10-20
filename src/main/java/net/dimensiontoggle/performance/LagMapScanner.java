package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// /lagmap: counts what every loaded chunk holds and ranks the chunks by a
// weighted "lag score". Runs on demand only (never in the background) and
// caches the result briefly, since several staff members tend to look at
// the same moment.
public class LagMapScanner {

    private static final long CACHE_MILLIS = 10_000;

    public enum Sort {
        SCORE, ENTITIES, MOBS, ITEMS, BLOCK_ENTITIES;

        public Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }

        Comparator<ChunkStats> comparator() {
            Comparator<ChunkStats> primary = switch (this) {
                case SCORE -> Comparator.comparingInt(ChunkStats::score);
                case ENTITIES -> Comparator.comparingInt(ChunkStats::entities);
                case MOBS -> Comparator.comparingInt(ChunkStats::mobs);
                case ITEMS -> Comparator.comparingInt(ChunkStats::items);
                case BLOCK_ENTITIES -> Comparator.comparingInt(ChunkStats::blockEntities);
            };
            return primary.thenComparingInt(ChunkStats::score).reversed();
        }
    }

    public record ChunkStats(String world, int x, int z, int entities, int mobs, int items,
                             int blockEntities, int hoppers, int score) {
    }

    public record CleanupResult(int items, int mobs) {
    }

    private final DimensionToggle plugin;
    private List<ChunkStats> cached = List.of();
    private long cachedAt;

    public LagMapScanner(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public List<ChunkStats> top(Sort sort, int limit) {
        if (System.currentTimeMillis() - cachedAt > CACHE_MILLIS) {
            cached = scan(true);
            cachedAt = System.currentTimeMillis();
        }
        return cached.stream().sorted(sort.comparator()).limit(limit).toList();
    }

    public void invalidate() {
        cachedAt = 0;
    }

    // Cheaper variant for the website: entities only, no block entities.
    public List<ChunkStats> quickTop(int limit) {
        return scan(false).stream().sorted(Sort.SCORE.comparator()).limit(limit).toList();
    }

    List<ChunkStats> scan(boolean includeBlockEntities) {
        ConfigurationSection weights = plugin.getConfigManager().getConfig().getConfigurationSection("lagmap.weights");
        int wMob = weight(weights, "mob", 3);
        int wItem = weight(weights, "item", 1);
        int wOther = weight(weights, "other-entity", 1);
        int wHopper = weight(weights, "hopper", 4);
        int wBlock = weight(weights, "block-entity", 1);

        List<ChunkStats> result = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            // [entities, mobs, items, blockEntities, hoppers]
            Map<Long, int[]> counts = new HashMap<>();
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                long key = Chunk.getChunkKey(((int) Math.floor(entity.getX())) >> 4, ((int) Math.floor(entity.getZ())) >> 4);
                int[] c = counts.computeIfAbsent(key, k -> new int[5]);
                c[0]++;
                if (entity instanceof Mob) {
                    c[1]++;
                } else if (entity instanceof Item || entity instanceof ExperienceOrb) {
                    c[2]++;
                }
            }
            if (includeBlockEntities) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    BlockState[] states = chunk.getTileEntities(false);
                    if (states.length == 0) {
                        continue;
                    }
                    int[] c = counts.computeIfAbsent(chunk.getChunkKey(), k -> new int[5]);
                    c[3] += states.length;
                    for (BlockState state : states) {
                        if (state instanceof Hopper) {
                            c[4]++;
                        }
                    }
                }
            }
            for (Map.Entry<Long, int[]> entry : counts.entrySet()) {
                int[] c = entry.getValue();
                int other = c[0] - c[1] - c[2];
                int score = c[1] * wMob + c[2] * wItem + other * wOther + c[4] * wHopper + (c[3] - c[4]) * wBlock;
                long key = entry.getKey();
                result.add(new ChunkStats(world.getName(), (int) key, (int) (key >> 32), c[0], c[1], c[2], c[3], c[4], score));
            }
        }
        return result;
    }

    private static int weight(ConfigurationSection weights, String key, int def) {
        return weights == null ? def : Math.max(0, weights.getInt(key, def));
    }

    // Shift-click cleanup: ground items, XP orbs and ordinary mobs. Anything
    // a player probably cares about is left alone - named, tamed or leashed
    // mobs, villagers/traders and anything carrying a player.
    public CleanupResult cleanup(String worldName, int x, int z) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return new CleanupResult(0, 0);
        }
        int items = 0;
        int mobs = 0;
        // Same chunk assignment as the scan, so exactly what was counted is
        // what gets cleaned - and an unloaded chunk is never loaded for it.
        for (Entity entity : world.getEntities()) {
            if ((((int) Math.floor(entity.getX())) >> 4) != x || (((int) Math.floor(entity.getZ())) >> 4) != z) {
                continue;
            }
            if (entity instanceof Item || entity instanceof ExperienceOrb) {
                entity.remove();
                items++;
            } else if (entity instanceof Mob mob && isDisposable(mob)) {
                mob.remove();
                mobs++;
            }
        }
        invalidate();
        return new CleanupResult(items, mobs);
    }

    static boolean isDisposable(Mob mob) {
        if (mob.customName() != null || mob.isLeashed() || mob instanceof AbstractVillager) {
            return false;
        }
        if (mob instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        for (Entity passenger : mob.getPassengers()) {
            if (passenger instanceof Player) {
                return false;
            }
        }
        return true;
    }
}
