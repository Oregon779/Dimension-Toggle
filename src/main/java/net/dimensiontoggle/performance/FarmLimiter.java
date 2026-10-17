package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Tameable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Big animal farms (dozens of cows in one pen) cost a lot of AI/pathfinding
// and collision work for nothing. Groups above a size get their AI and
// collision switched off; hitting one of them wakes that animal up again
// (see FreezeListener), so killing/collecting keeps working normally.
public class FarmLimiter {

    private final DimensionToggle plugin;
    private BukkitTask task;

    public FarmLimiter(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long interval = Math.max(5, plugin.getConfigManager().getConfig()
                .getInt("farm-limiter.check-interval-seconds", 30)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 613L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isConfiguredOn() {
        return plugin.getConfigManager().getConfig().getBoolean("farm-limiter.enabled", false);
    }

    // On while switched on - or while the auto performance mode is active.
    public boolean isActive() {
        return isConfiguredOn() || plugin.getAutoPerformanceManager().isActive();
    }

    public int groupSize() {
        int configured = Math.max(2, plugin.getConfigManager().getConfig().getInt("farm-limiter.group-size", 20));
        if (plugin.getAutoPerformanceManager().isActive()) {
            int strict = plugin.getConfigManager().getConfig().getInt("auto-performance.farm-group-size", 10);
            return Math.max(2, Math.min(configured, strict));
        }
        return configured;
    }

    // Package-private for tests. Returns how many animals were frozen.
    int scan() {
        if (!isActive()) {
            return 0;
        }
        int size = groupSize();
        int frozen = 0;
        for (World world : Bukkit.getWorlds()) {
            Map<Long, Map<EntityType, List<Animals>>> groups = new HashMap<>();
            for (Animals animal : world.getEntitiesByClass(Animals.class)) {
                if (!eligible(animal)) {
                    continue;
                }
                long key = Chunk.getChunkKey(((int) Math.floor(animal.getX())) >> 4, ((int) Math.floor(animal.getZ())) >> 4);
                groups.computeIfAbsent(key, k -> new HashMap<>())
                        .computeIfAbsent(animal.getType(), t -> new ArrayList<>()).add(animal);
            }
            for (Map<EntityType, List<Animals>> byType : groups.values()) {
                for (List<Animals> group : byType.values()) {
                    if (group.size() < size) {
                        continue;
                    }
                    for (Animals animal : group) {
                        if (EntityFreezer.reason(plugin, animal) == null) {
                            EntityFreezer.freeze(plugin, animal, EntityFreezer.FARM, true);
                            frozen++;
                        }
                    }
                }
            }
        }
        return frozen;
    }

    // Pets, named and leashed animals and anything someone rides keep their AI.
    static boolean eligible(Animals animal) {
        if (animal.customName() != null || animal.isLeashed() || !animal.getPassengers().isEmpty()) {
            return false;
        }
        return !(animal instanceof Tameable tameable && tameable.isTamed());
    }

    public int unfreezeAll() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                if (EntityFreezer.FARM.equals(EntityFreezer.reason(plugin, mob))) {
                    EntityFreezer.unfreeze(plugin, mob);
                    count++;
                }
            }
        }
        return count;
    }

    public int countFrozen() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Animals.class)) {
                if (EntityFreezer.FARM.equals(EntityFreezer.reason(plugin, (Mob) entity))) {
                    count++;
                }
            }
        }
        return count;
    }
}
