package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobManagementManager implements Listener {

    public static final List<EntityType> NETHER_MOBS = List.of(
            EntityType.BLAZE, EntityType.GHAST, EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON,
            EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
            EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.STRIDER, EntityType.ENDERMAN
    );

    public static final List<EntityType> END_MOBS = List.of(
            EntityType.ENDERMAN, EntityType.SHULKER, EntityType.ENDER_DRAGON
    );

    private static final java.util.Set<EntityType> NETHER_MOBS_SET = java.util.EnumSet.copyOf(NETHER_MOBS);
    private static final java.util.Set<EntityType> END_MOBS_SET = java.util.EnumSet.copyOf(END_MOBS);

    public static final int[] CLEANUP_PRESETS_MINUTES = {0, 5, 10, 30, 60};

    private final DimensionToggle plugin;
    private final Map<String, Boolean> spawnEnabled = new HashMap<>();
    private final Map<String, Integer> cleanupMinutes = new HashMap<>();
    private final Map<String, Integer> minutesSinceCleanup = new HashMap<>();
    private BukkitTask task;

    public MobManagementManager(DimensionToggle plugin) {
        this.plugin = plugin;
        loadState();
    }

    private void loadState() {
        for (ToggleDimension dimension : ToggleDimension.values()) {
            for (EntityType type : mobsFor(dimension)) {
                String key = key(dimension, type);
                spawnEnabled.put(key, plugin.getConfigManager().getData()
                        .getBoolean("mob-management." + dimension.getKey() + "." + type.name() + ".enabled", true));
                cleanupMinutes.put(key, plugin.getConfigManager().getData()
                        .getInt("mob-management." + dimension.getKey() + "." + type.name() + ".cleanup-minutes", 0));
            }
        }
    }

    public static List<EntityType> mobsFor(ToggleDimension dimension) {
        return dimension == ToggleDimension.NETHER ? NETHER_MOBS : END_MOBS;
    }

    private String key(ToggleDimension dimension, EntityType type) {
        return dimension.getKey() + ":" + type.name();
    }

    public boolean isSpawnEnabled(ToggleDimension dimension, EntityType type) {
        return spawnEnabled.getOrDefault(key(dimension, type), true);
    }

    public boolean toggleSpawnEnabled(ToggleDimension dimension, EntityType type) {
        boolean newValue = !isSpawnEnabled(dimension, type);
        spawnEnabled.put(key(dimension, type), newValue);
        plugin.getConfigManager().getData()
                .set("mob-management." + dimension.getKey() + "." + type.name() + ".enabled", newValue);
        plugin.getConfigManager().saveData();
        return newValue;
    }

    public int getCleanupMinutes(ToggleDimension dimension, EntityType type) {
        return cleanupMinutes.getOrDefault(key(dimension, type), 0);
    }

    public int getMinutesUntilNextCleanup(ToggleDimension dimension, EntityType type) {
        int interval = getCleanupMinutes(dimension, type);
        if (interval <= 0) {
            return -1;
        }
        int elapsed = minutesSinceCleanup.getOrDefault(key(dimension, type), 0);
        return Math.max(0, interval - elapsed);
    }

    public int cycleCleanupMinutes(ToggleDimension dimension, EntityType type) {
        int current = getCleanupMinutes(dimension, type);
        int currentIndex = 0;
        for (int i = 0; i < CLEANUP_PRESETS_MINUTES.length; i++) {
            if (CLEANUP_PRESETS_MINUTES[i] == current) {
                currentIndex = i;
                break;
            }
        }
        int next = CLEANUP_PRESETS_MINUTES[(currentIndex + 1) % CLEANUP_PRESETS_MINUTES.length];

        String key = key(dimension, type);
        cleanupMinutes.put(key, next);
        minutesSinceCleanup.put(key, 0);
        plugin.getConfigManager().getData()
                .set("mob-management." + dimension.getKey() + "." + type.name() + ".cleanup-minutes", next);
        plugin.getConfigManager().saveData();
        return next;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCleanup, 20L * 60, 20L * 60);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickCleanup() {
        for (ToggleDimension dimension : ToggleDimension.values()) {
            for (EntityType type : mobsFor(dimension)) {
                int interval = getCleanupMinutes(dimension, type);
                if (interval <= 0) {
                    continue;
                }
                String key = key(dimension, type);
                int elapsed = minutesSinceCleanup.getOrDefault(key, 0) + 1;
                if (elapsed >= interval) {
                    removeAll(dimension, type);
                    minutesSinceCleanup.put(key, 0);
                } else {
                    minutesSinceCleanup.put(key, elapsed);
                }
            }
        }
    }

    private void removeAll(ToggleDimension dimension, EntityType type) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != dimension.getEnvironment()) {
                continue;
            }
            List<Entity> toRemove = new ArrayList<>();
            for (Entity entity : world.getEntities()) {
                if (entity.getType() == type) {
                    toRemove.add(entity);
                }
            }
            for (Entity entity : toRemove) {
                entity.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        World.Environment environment = event.getEntity().getWorld().getEnvironment();
        ToggleDimension dimension = environment == World.Environment.NETHER ? ToggleDimension.NETHER
                : environment == World.Environment.THE_END ? ToggleDimension.END : null;
        if (dimension == null) {
            return;
        }
        java.util.Set<EntityType> managedMobs = dimension == ToggleDimension.NETHER ? NETHER_MOBS_SET : END_MOBS_SET;
        if (!managedMobs.contains(event.getEntityType())) {
            return;
        }
        if (!isSpawnEnabled(dimension, event.getEntityType())) {
            event.setCancelled(true);
        }
    }
}
