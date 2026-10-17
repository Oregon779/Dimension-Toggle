package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

// Samples server health every 5 seconds on the main thread and publishes an
// immutable snapshot, so the editor's dashboard (redrawn every second) reads
// a cached value instead of walking all worlds itself.
public class PerformanceMonitor {

    public static final int SAMPLE_INTERVAL_TICKS = 100;

    public record WorldStats(String name, int players, int chunks, int entities) {
    }

    public record Snapshot(long timestamp, double tps1, double mspt, int online, int maxPlayers,
                           long usedMemoryMb, long maxMemoryMb, List<WorldStats> worlds) {
    }

    private final DimensionToggle plugin;
    private volatile Snapshot latest;
    private BukkitTask task;
    // Test seam: MockBukkit has no real tick loop to measure.
    private DoubleSupplier msptSource = () -> Bukkit.getServer().getAverageTickTime();

    public PerformanceMonitor(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        sample();
        // Odd offset: stays off the ticks of the other repeating tasks.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 101L, SAMPLE_INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    void setMsptSource(DoubleSupplier source) {
        this.msptSource = source;
    }

    public double currentMspt() {
        return msptSource.getAsDouble();
    }

    public Snapshot latest() {
        Snapshot snapshot = latest;
        return snapshot != null ? snapshot : sample();
    }

    Snapshot sample() {
        double[] tps = Bukkit.getServer().getTPS();
        List<WorldStats> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            worlds.add(new WorldStats(world.getName(), world.getPlayerCount(), world.getChunkCount(),
                    world.getEntityCount()));
        }

        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long max = runtime.maxMemory() / (1024 * 1024);

        Snapshot snapshot = new Snapshot(System.currentTimeMillis(), round(tps[0]), round(currentMspt()),
                Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers(), used, max, List.copyOf(worlds));
        latest = snapshot;
        return snapshot;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
