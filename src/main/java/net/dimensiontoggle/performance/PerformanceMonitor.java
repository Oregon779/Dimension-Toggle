package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

// Samples server health every 5 seconds on the main thread and publishes an
// immutable snapshot. The web server (other threads) only ever reads the
// published snapshot/history - it never touches the Bukkit API itself.
public class PerformanceMonitor {

    public static final int SAMPLE_INTERVAL_TICKS = 100;
    // One hour of history at one sample every 5 seconds.
    public static final int HISTORY_SIZE = 720;

    public record WorldStats(String name, String environment, int players, int chunks, int entities,
                             int blockEntities) {
    }

    public record Snapshot(long timestamp, double tps1, double tps5, double tps15, double mspt, double msptMax,
                           int online, int maxPlayers, long usedMemoryMb, long maxMemoryMb, double cpuPercent,
                           long uptimeSeconds, List<WorldStats> worlds, Map<String, String> dimensions,
                           List<LagMapScanner.ChunkStats> topChunks, boolean autoPerformanceActive) {
    }

    private final DimensionToggle plugin;
    private final long startedAt = System.currentTimeMillis();
    private final double[] msptHistory = new double[HISTORY_SIZE];
    private final double[] tpsHistory = new double[HISTORY_SIZE];
    private int historyCount;
    private int historyNext;
    private volatile Snapshot latest;
    private BukkitTask task;
    private int samplesSinceChunkScan;
    private List<LagMapScanner.ChunkStats> topChunks = List.of();
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

    // Oldest first.
    public synchronized double[][] history() {
        double[] mspt = new double[historyCount];
        double[] tps = new double[historyCount];
        int start = (historyNext - historyCount + HISTORY_SIZE) % HISTORY_SIZE;
        for (int i = 0; i < historyCount; i++) {
            int index = (start + i) % HISTORY_SIZE;
            mspt[i] = msptHistory[index];
            tps[i] = tpsHistory[index];
        }
        return new double[][]{mspt, tps};
    }

    Snapshot sample() {
        double[] tps = Bukkit.getServer().getTPS();
        double mspt = currentMspt();
        double msptMax = 0;
        long[] tickTimes = Bukkit.getServer().getTickTimes();
        if (tickTimes != null) {
            for (long nanos : tickTimes) {
                msptMax = Math.max(msptMax, nanos / 1_000_000.0);
            }
        }

        List<WorldStats> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            worlds.add(new WorldStats(world.getName(), world.getEnvironment().name(), world.getPlayerCount(),
                    world.getChunkCount(), world.getEntityCount(), world.getTileEntityCount()));
        }

        Map<String, String> dimensions = new LinkedHashMap<>();
        for (ToggleDimension dimension : ToggleDimension.values()) {
            String state;
            if (plugin.getMaintenanceManager().isPending(dimension)) {
                state = "maintenance";
            } else if (plugin.getDimensionManager().isLocked(dimension)) {
                state = "locked";
            } else {
                state = plugin.getDimensionManager().isEnabled(dimension) ? "enabled" : "disabled";
            }
            dimensions.put(dimension.getKey(), state);
        }

        // The top chunks are only needed by the website; refresh them once a
        // minute (entity-only scan, no block entities) while it is running.
        if (plugin.getPerformanceWebServer() != null && plugin.getPerformanceWebServer().isRunning()
                && (samplesSinceChunkScan++ % 12 == 0)) {
            topChunks = plugin.getLagMapScanner().quickTop(5);
        }

        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long max = runtime.maxMemory() / (1024 * 1024);

        Snapshot snapshot = new Snapshot(System.currentTimeMillis(), round(tps[0]), round(tps[1]), round(tps[2]),
                round(mspt), round(msptMax), Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers(), used, max,
                cpuPercent(), (System.currentTimeMillis() - startedAt) / 1000, List.copyOf(worlds),
                Map.copyOf(dimensions), topChunks,
                plugin.getAutoPerformanceManager() != null && plugin.getAutoPerformanceManager().isActive());
        record(snapshot);
        latest = snapshot;
        return snapshot;
    }

    private synchronized void record(Snapshot snapshot) {
        msptHistory[historyNext] = snapshot.mspt();
        tpsHistory[historyNext] = snapshot.tps1();
        historyNext = (historyNext + 1) % HISTORY_SIZE;
        historyCount = Math.min(HISTORY_SIZE, historyCount + 1);
    }

    private static double cpuPercent() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            double load = os.getProcessCpuLoad();
            return load < 0 ? -1 : round(load * 100);
        }
        return -1;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
