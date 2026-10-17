package net.dimensiontoggle.performance;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.dimensiontoggle.DimensionToggle;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Finds fast/large redstone contraptions: counts redstone power changes and
// piston moves per chunk over a 20-second window. The event handlers run for
// every single redstone update on the server, so they only do a primitive
// map increment - all evaluation happens once per window.
public class RedstoneMeter implements Listener {

    public static final int WINDOW_SECONDS = 20;
    private static final long ALERT_COOLDOWN_MILLIS = 5 * 60 * 1000;

    public record ChunkRate(String world, int x, int z, double perSecond) {
    }

    private final DimensionToggle plugin;
    private final Map<World, Long2IntOpenHashMap> counts = new HashMap<>();
    private final Map<String, Long> lastAlert = new HashMap<>();
    private List<ChunkRate> lastWindow = List.of();
    private BukkitTask task;

    public RedstoneMeter(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::closeWindow,
                WINDOW_SECONDS * 20L + 7, WINDOW_SECONDS * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        counts.clear();
    }

    private boolean enabled() {
        return plugin.getConfigManager().getConfig().getBoolean("redstone-meter.enabled", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstone(BlockRedstoneEvent event) {
        count(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        count(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        count(event.getBlock());
    }

    private void count(Block block) {
        if (task == null) {
            return;
        }
        counts.computeIfAbsent(block.getWorld(), w -> new Long2IntOpenHashMap())
                .addTo(Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4), 1);
    }

    // Package-private for tests.
    void closeWindow() {
        if (!enabled()) {
            counts.clear();
            lastWindow = List.of();
            return;
        }
        List<ChunkRate> rates = new ArrayList<>();
        for (Map.Entry<World, Long2IntOpenHashMap> worldEntry : counts.entrySet()) {
            for (Long2IntMap.Entry entry : worldEntry.getValue().long2IntEntrySet()) {
                long key = entry.getLongKey();
                rates.add(new ChunkRate(worldEntry.getKey().getName(), (int) key, (int) (key >> 32),
                        Math.round(entry.getIntValue() * 10.0 / WINDOW_SECONDS) / 10.0));
            }
        }
        counts.clear();
        rates.sort(Comparator.comparingDouble(ChunkRate::perSecond).reversed());
        lastWindow = List.copyOf(rates.subList(0, Math.min(50, rates.size())));
        alert();
    }

    private void alert() {
        double threshold = plugin.getConfigManager().getConfig().getDouble("redstone-meter.alert-per-second", 100);
        if (threshold <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        lastAlert.values().removeIf(at -> now - at > ALERT_COOLDOWN_MILLIS);
        List<Player> staff = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("dimensiontoggle.performance.notify")) {
                staff.add(player);
            }
        }
        for (ChunkRate rate : lastWindow) {
            if (rate.perSecond() < threshold) {
                break; // sorted descending
            }
            String id = rate.world() + ":" + rate.x() + ":" + rate.z();
            if (lastAlert.putIfAbsent(id, now) != null) {
                continue;
            }
            plugin.getLogger().warning("Fast redstone in " + rate.world() + " chunk " + rate.x() + ", " + rate.z()
                    + ": " + rate.perSecond() + " updates/s");
            if (!staff.isEmpty()) {
                plugin.getChunkReports().sendAlert(staff, "redstone-alert", rate.world(), rate.x(), rate.z(),
                        Map.of("rate", String.valueOf(rate.perSecond())));
            }
        }
    }

    public List<ChunkRate> top(int limit) {
        return lastWindow.subList(0, Math.min(limit, lastWindow.size()));
    }
}
