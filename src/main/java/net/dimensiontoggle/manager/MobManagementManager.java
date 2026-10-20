package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.WorldScope;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
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

    // Every living Overworld mob, grouped the way the mob menu shows them.
    public enum Category {
        MONSTERS(List.of(
                EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER,
                EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.PARCHED,
                EntityType.CREEPER, EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.ENDERMAN,
                EntityType.ENDERMITE, EntityType.WITCH, EntityType.SLIME, EntityType.SULFUR_CUBE,
                EntityType.PHANTOM, EntityType.SILVERFISH, EntityType.PILLAGER, EntityType.VINDICATOR,
                EntityType.EVOKER, EntityType.VEX, EntityType.RAVAGER, EntityType.BREEZE, EntityType.CREAKING)),
        ANIMALS(List.of(
                EntityType.COW, EntityType.MOOSHROOM, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN,
                EntityType.RABBIT, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
                EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE, EntityType.CAMEL, EntityType.CAMEL_HUSK,
                EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.GOAT, EntityType.WOLF, EntityType.CAT,
                EntityType.OCELOT, EntityType.FOX, EntityType.PANDA, EntityType.POLAR_BEAR, EntityType.PARROT,
                EntityType.BEE, EntityType.BAT, EntityType.FROG, EntityType.ARMADILLO, EntityType.SNIFFER,
                EntityType.HAPPY_GHAST)),
        WATER(List.of(
                EntityType.COD, EntityType.SALMON, EntityType.PUFFERFISH, EntityType.TROPICAL_FISH,
                EntityType.SQUID, EntityType.GLOW_SQUID, EntityType.DOLPHIN, EntityType.TURTLE,
                EntityType.AXOLOTL, EntityType.TADPOLE, EntityType.NAUTILUS, EntityType.ZOMBIE_NAUTILUS,
                EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN)),
        OTHER(List.of(
                EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM,
                EntityType.COPPER_GOLEM, EntityType.ALLAY, EntityType.WARDEN, EntityType.WITHER));

        private final List<EntityType> mobs;

        Category(List<EntityType> mobs) {
            this.mobs = mobs;
        }

        public List<EntityType> mobs() {
            return mobs;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final List<EntityType> OVERWORLD_MOBS = java.util.Arrays.stream(Category.values())
            .flatMap(category -> category.mobs().stream())
            .toList();

    // Interval limits for a custom cleanup time (chat input).
    public static final int MIN_CLEANUP_MINUTES = 1;
    public static final int MAX_CLEANUP_MINUTES = 24 * 60;

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

    // Sets a custom cleanup interval (0 = off), as typed in the chat.
    public void setCleanupMinutes(WorldScope dimension, EntityType type, int minutes) {
        int value = minutes <= 0 ? 0 : Math.clamp(minutes, MIN_CLEANUP_MINUTES, MAX_CLEANUP_MINUTES);
        cleanupMinutes.computeIfAbsent(dimension, d -> new EnumMap<>(EntityType.class)).put(type, value);
        minutesSinceCleanup.computeIfAbsent(dimension, d -> new EnumMap<>(EntityType.class)).put(type, 0);
        plugin.getConfigManager().getData()
                .set("mob-management." + dimension.getKey() + "." + type.name() + ".cleanup-minutes", value);
        plugin.getConfigManager().saveData();
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
                if (types.contains(entity.getType()) && !carriesPlayer(entity) && !isProtected(entity)) {
                    entity.remove();
                }
            }
        }
    }

    // Mobs players obviously care about survive the automatic cleanup
    // (each switchable in config.yml under mob-management.protect-*).
    private boolean isProtected(Entity entity) {
        var config = plugin.getConfigManager().getConfig();
        if (entity.customName() != null && config.getBoolean("mob-management.protect-named", true)) {
            return true;
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()
                && config.getBoolean("mob-management.protect-tamed", true)) {
            return true;
        }
        return entity instanceof Villager villager
                && villager.getProfession() != Villager.Profession.NONE
                && villager.getProfession() != Villager.Profession.NITWIT
                && config.getBoolean("mob-management.protect-villagers-with-profession", true);
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
