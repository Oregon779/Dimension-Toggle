package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Watches MSPT. Under sustained load it switches into a performance mode:
// AFK view reduction and the farm limiter run (stricter) even if they are
// switched off; once the load is gone again, everything goes back to normal.
public class AutoPerformanceManager {

    static final int CHECK_INTERVAL_SECONDS = 5;
    static final int ACTIVATE_AFTER_SECONDS = 60;
    static final int RECOVER_AFTER_SECONDS = 120;

    private final DimensionToggle plugin;
    private BukkitTask task;
    private boolean active;
    private int secondsOver;
    private int secondsUnder;

    public AutoPerformanceManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 811L, CHECK_INTERVAL_SECONDS * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("auto-performance.enabled", false);
    }

    public boolean isActive() {
        return active;
    }

    // Package-private for tests.
    void check() {
        if (!isEnabled()) {
            if (active) {
                setActive(false, 0);
            }
            secondsOver = 0;
            secondsUnder = 0;
            return;
        }
        double mspt = plugin.getPerformanceMonitor().currentMspt();
        double activateAt = plugin.getConfigManager().getConfig().getDouble("auto-performance.activate-mspt", 45);
        double recoverAt = plugin.getConfigManager().getConfig().getDouble("auto-performance.recover-mspt", 35);

        if (!active) {
            secondsOver = mspt >= activateAt ? secondsOver + CHECK_INTERVAL_SECONDS : 0;
            if (secondsOver >= ACTIVATE_AFTER_SECONDS) {
                setActive(true, mspt);
            }
        } else {
            secondsUnder = mspt <= recoverAt ? secondsUnder + CHECK_INTERVAL_SECONDS : 0;
            if (secondsUnder >= RECOVER_AFTER_SECONDS) {
                setActive(false, mspt);
            }
        }
    }

    private void setActive(boolean value, double mspt) {
        active = value;
        secondsOver = 0;
        secondsUnder = 0;
        String rounded = String.valueOf(Math.round(mspt * 10) / 10.0);
        plugin.getLogger().warning(value
                ? "Auto performance mode ON (" + rounded + " ms/tick)"
                : "Auto performance mode OFF (" + rounded + " ms/tick)");
        plugin.getLogManager().logByName(null, value ? "AUTO PERFORMANCE ON" : "AUTO PERFORMANCE OFF", null,
                rounded + " ms/tick");

        if (value) {
            plugin.getFarmLimiter().scan();
        } else {
            // Leave behind only what the admin switched on themselves.
            if (!plugin.getFarmLimiter().isConfiguredOn()) {
                plugin.getFarmLimiter().unfreezeAll();
            }
            if (!plugin.getAfkViewManager().isConfiguredOn()) {
                plugin.getAfkViewManager().restoreAll();
            }
        }

        List<Player> staff = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("dimensiontoggle.performance.notify")) {
                staff.add(player);
            }
        }
        plugin.getMessageManager().sendToAll(staff,
                value ? "auto-performance-activated" : "auto-performance-deactivated", Map.of("mspt", rounded));
    }
}
