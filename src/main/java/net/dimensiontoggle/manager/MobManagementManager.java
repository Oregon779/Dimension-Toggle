package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.WorldScope;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MobManagementManager implements Listener {

    public static final List<EntityType> NETHER_MOBS = List.of(
            EntityType.BLAZE, EntityType.GHAST, EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON,
            EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
            EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.STRIDER, EntityType.ENDERMAN
    );

    public static final List<EntityType> END_MOBS = List.of(
            EntityType.ENDERMAN, EntityType.SHULKER, EntityType.ENDER_DRAGON
    );

    // 28 = exactly the interior of the 6-row mob menu.
    public static final List<EntityType> OVERWORLD_MOBS = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.ENDERMAN, EntityType.WITCH, EntityType.SLIME,
            EntityType.PHANTOM, EntityType.DROWNED, EntityType.HUSK, EntityType.STRAY,
            EntityType.PILLAGER, EntityType.ZOMBIE_VILLAGER, EntityType.SILVERFISH, EntityType.BOGGED,
            EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
            EntityType.RABBIT, EntityType.HORSE, EntityType.GOAT, EntityType.SQUID,
            EntityType.GLOW_SQUID, EntityType.BAT, EntityType.COD, EntityType.SALMON
    );

    private static final Map<WorldScope, Set<EntityType>> MANAGED = new EnumMap<>(Map.of(
            WorldScope.OVERWORLD, EnumSet.copyOf(OVERWORLD_MOBS),
            WorldScope.NETHER, EnumSet.copyOf(NETHER_MOBS),
            WorldScope.END, EnumSet.copyOf(END_MOBS)));

    public static final int[] CLEANUP_PRESETS_MINUTES = {0, 5, 10, 30, 60};

    private final DimensionToggle plugin;
    // Keyed by dimension then entity type (EnumMap, not a String-concat key) so the
    // CreatureSpawnEvent handler below - which fires for every mob spawn server-wide -
    // never has to allocate a new String just to look up whether a type may spawn.
    private final Map<WorldScope, Map<EntityType, Boolean>> spawnEnabled = new EnumMap<>(WorldScope.class);
    private final Map<WorldScope, Map<EntityType, Integer>> cleanupMinutes = new EnumMap<>(WorldScope.class);
    private final Map<WorldScope, Map<EntityType, Integer>> minutesSinceCleanup = new EnumMap<>(WorldScope.class);
    private BukkitTask task;

    public MobManagementManager(DimensionToggle plugin) {
        this.plugin = plugin;
        loadState();
    }

    private void loadState() {
        for (WorldScope dimension : WorldScope.values()) {
            Map<EntityType, Boolean> spawnMap = new EnumMap<>(EntityType.class);
            Map<EntityType, Integer> cleanupMap = new EnumMap<>(EntityType.class);
            for (EntityType type : mobsFor(dimension)) {
                spawnMap.put(type, plugin.getConfigManager().getData()
                        .getBoolean("mob-management." + dimension.getKey() + "." + type.name() + ".enabled", true));
                cleanupMap.put(type, plugin.getConfigManager().getData()
                        .getInt("mob-management." + dimension.getKey() + "." + type.name() + ".cleanup-minutes", 0));
            }
            spawnEnabled.put(dimension, spawnMap);
            cleanupMinutes.put(dimension, cleanupMap);
            minutesSinceCleanup.put(dimension, new EnumMap<>(EntityType.class));
        }
    }

    public static List<EntityType> mobsFor(WorldScope scope) {
        return switch (scope) {
            case OVERWORLD -> OVERWORLD_MOBS;
            case NETHER -> NETHER_MOBS;
            case END -> END_MOBS;
        };
    }

    public boolean isSpawnEnabled(WorldScope dimension, EntityType type) {
        Map<EntityType, Boolean> map = spawnEnabled.get(dimension);
        return map == null || map.getOrDefault(type, true);
    }

    public boolean toggleSpawnEnabled(WorldScope dimension, EntityType type) {
        boolean newValue = !isSpawnEnabled(dimension, type);
        spawnEnabled.computeIfAbsent(dimension, d -> new EnumMap<>(EntityType.class)).put(type, newValue);
        plugin.getConfigManager().getData()
                .set("mob-management." + dimension.getKey() + "." + type.name() + ".enabled", newValue);
        plugin.getConfigManager().saveData();
        return newValue;
    }

    public int getCleanupMinutes(WorldScope dimension, EntityType type) {
        Map<EntityType, Integer> map = cleanupMinutes.get(dimension);
        return map == null ? 0 : map.getOrDefault(type, 0);
    }

    public int getMinutesUntilNextCleanup(WorldScope dimension, EntityType type) {
        int interval = getCleanupMinutes(dimension, type);
        if (interval <= 0) {
            return -1;
        }
        Map<EntityType, Integer> map = minutesSinceCleanup.get(dimension);
        int elapsed = map == null ? 0 : map.getOrDefault(type, 0);
        return Math.max(0, interval - elapsed);
    }

    public int cycleCleanupMinutes(WorldScope dimension, EntityType type) {
        int current = getCleanupMinutes(dimension, type);
        int currentIndex = 0;
        for (int i = 0; i < CLEANUP_PRESETS_MINUTES.length; i++) {
            if (CLEANUP_PRESETS_MINUTES[i] == current) {
                currentIndex = i;
                break;
            }
        }
        int next = CLEANUP_PRESETS_MINUTES[(currentIndex + 1) % CLEANUP_PRESETS_MINUTES.length];

        cleanupMinutes.computeIfAbsent(dimension, d -> new EnumMap<>(EntityType.class)).put(type, next);
        minutesSinceCleanup.computeIfAbsent(dimension, d -> new EnumMap<>(EntityType.class)).put(type, 0);
        plugin.getConfigManager().getData()
                .set("mob-management." + dimension.getKey() + "." + type.name() + ".cleanup-minutes", next);
        plugin.getConfigManager().saveData();
        return next;
    }

    public void start() {
        // Offset start (see PeakPlayerManager) so the entity scan never shares a
        // tick with the other repeating tasks.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCleanup, 1211L, 20L * 60);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickCleanup() {
        for (WorldScope dimension : WorldScope.values()) {
            Map<EntityType, Integer> map = minutesSinceCleanup.computeIfAbsent(dimension, d -> new EnumMap<>(EntityType.class));
            Set<EntityType> due = EnumSet.noneOf(EntityType.class);
            for (EntityType type : mobsFor(dimension)) {
                int interval = getCleanupMinutes(dimension, type);
                if (interval <= 0) {
                    continue;
                }
                int elapsed = map.getOrDefault(type, 0) + 1;
                if (elapsed >= interval) {
                    due.add(type);
                    map.put(type, 0);
                } else {
                    map.put(type, elapsed);
                }
            }
            if (!due.isEmpty()) {
                removeAll(dimension, due);
            }
        }
    }

    // One pass over each world's entity list for all types that are due,
    // instead of one full scan per type (Nether has 11 managed types, and
    // with many players loading chunks the entity list gets long).
    private void removeAll(WorldScope dimension, Set<EntityType> types) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != dimension.getEnvironment()) {
                continue;
            }
            // getEntities() returns a copy, so removing while iterating is safe.
            for (Entity entity : world.getEntities()) {
                if (types.contains(entity.getType()) && !carriesPlayer(entity)) {
                    entity.remove();
                }
            }
        }
    }

    // A strider someone is riding over a lava lake must not vanish from
    // under them.
    private static boolean carriesPlayer(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        WorldScope scope = WorldScope.fromEnvironment(event.getEntity().getWorld().getEnvironment());
        if (scope == null || !MANAGED.get(scope).contains(event.getEntityType())) {
            return;
        }
        if (!isSpawnEnabled(scope, event.getEntityType())) {
            event.setCancelled(true);
        }
    }
}
