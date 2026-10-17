package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Trading halls: dozens of villagers boxed into single cells keep running
// their full AI (pathfinding, gossip, POI searches) - one of the biggest
// sources of lag. Villagers that haven't moved for a while lose their AI;
// trading still works, and since restocking normally needs the AI to walk
// to the workstation, they are restocked here twice per Minecraft day.
public class VillagerOptimizer {

    public static final long CHECK_INTERVAL_TICKS = 1200L;
    // Half a Minecraft day.
    static final long RESTOCK_INTERVAL_TICKS = 12000L;
    private static final String VILLAGER_REASON = EntityFreezer.VILLAGER;

    private final DimensionToggle plugin;
    private final Map<UUID, double[]> lastPosition = new HashMap<>();
    private final Map<UUID, Integer> idleChecks = new HashMap<>();
    private BukkitTask task;

    public VillagerOptimizer(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 1217L, CHECK_INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("villager-optimizer.enabled", false);
    }

    private NamespacedKey restockKey() {
        return new NamespacedKey(plugin, "last-restock");
    }

    // Package-private for tests: one check = one minute of real time.
    void check() {
        if (!isEnabled()) {
            lastPosition.clear();
            idleChecks.clear();
            return;
        }
        int idleMinutes = Math.max(1, plugin.getConfigManager().getConfig().getInt("villager-optimizer.idle-minutes", 5));
        Set<UUID> seen = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            long now = world.getFullTime();
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                UUID id = villager.getUniqueId();
                seen.add(id);
                if (VILLAGER_REASON.equals(EntityFreezer.reason(plugin, villager))) {
                    restockIfDue(villager, now);
                    continue;
                }
                if (villager.customName() != null || villager.isLeashed()) {
                    continue;
                }
                double[] pos = {villager.getX(), villager.getY(), villager.getZ()};
                double[] before = lastPosition.put(id, pos);
                boolean stood = before != null && distanceSquared(before, pos) < 0.25;
                int idle = stood ? idleChecks.merge(id, 1, Integer::sum) : 0;
                if (!stood) {
                    idleChecks.remove(id);
                }
                if (idle >= idleMinutes) {
                    EntityFreezer.freeze(plugin, villager, VILLAGER_REASON, false);
                    villager.getPersistentDataContainer().set(restockKey(), PersistentDataType.LONG, now);
                    lastPosition.remove(id);
                    idleChecks.remove(id);
                }
            }
        }
        lastPosition.keySet().retainAll(seen);
        idleChecks.keySet().retainAll(seen);
    }

    private void restockIfDue(Villager villager, long now) {
        Long last = villager.getPersistentDataContainer().get(restockKey(), PersistentDataType.LONG);
        if (last == null || now - last >= RESTOCK_INTERVAL_TICKS || now < last) {
            villager.restock();
            villager.getPersistentDataContainer().set(restockKey(), PersistentDataType.LONG, now);
        }
    }

    private static double distanceSquared(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    public int unfreezeAll() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Mob mob : world.getEntitiesByClass(Villager.class)) {
                if (VILLAGER_REASON.equals(EntityFreezer.reason(plugin, mob))) {
                    EntityFreezer.unfreeze(plugin, mob);
                    count++;
                }
            }
        }
        return count;
    }

    public int countOptimized() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (VILLAGER_REASON.equals(EntityFreezer.reason(plugin, villager))) {
                    count++;
                }
            }
        }
        return count;
    }
}
