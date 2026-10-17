package net.dimensiontoggle.performance;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dimensiontoggle.DimensionToggle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Lowers the view (and optionally simulation) distance of players who are
// AFK, and restores it the moment they do something again. A server with
// many AFK players saves a lot of chunk sending and ticking this way.
public class AfkViewManager implements Listener {

    private record Saved(int view, int send, int simulation) {
    }

    private final DimensionToggle plugin;
    // Written from the async chat thread too.
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Saved> reduced = new HashMap<>();
    private BukkitTask task;

    public AfkViewManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            lastActivity.putIfAbsent(player.getUniqueId(), now);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 409L, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        restoreAll();
    }

    public boolean isConfiguredOn() {
        return plugin.getConfigManager().getConfig().getBoolean("afk-view.enabled", false);
    }

    public boolean isActive() {
        return isConfiguredOn() || plugin.getAutoPerformanceManager().isActive();
    }

    public int countReduced() {
        return reduced.size();
    }

    long afkMillis() {
        int minutes = plugin.getConfigManager().getConfig().getInt("afk-view.afk-minutes", 5);
        if (plugin.getAutoPerformanceManager().isActive()) {
            minutes = Math.min(minutes, plugin.getConfigManager().getConfig().getInt("auto-performance.afk-minutes", 2));
        }
        return Math.max(1, minutes) * 60_000L;
    }

    // Package-private for tests.
    void check() {
        if (!isActive()) {
            restoreAll();
            return;
        }
        long now = System.currentTimeMillis();
        long afk = afkMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (reduced.containsKey(id) || player.hasPermission("dimensiontoggle.afk.bypass")) {
                continue;
            }
            long last = lastActivity.computeIfAbsent(id, k -> now);
            if (now - last >= afk) {
                reduce(player);
            }
        }
    }

    private void reduce(Player player) {
        int view = plugin.getConfigManager().getConfig().getInt("afk-view.view-distance", 2);
        int simulation = plugin.getConfigManager().getConfig().getInt("afk-view.simulation-distance", -1);
        reduced.put(player.getUniqueId(), new Saved(player.getViewDistance(), player.getSendViewDistance(),
                player.getSimulationDistance()));
        if (view > 0) {
            player.setViewDistance(Math.max(2, view));
            player.setSendViewDistance(Math.max(2, view));
        }
        if (simulation > 0) {
            player.setSimulationDistance(Math.max(2, simulation));
        }
        if (plugin.getConfigManager().getConfig().getBoolean("afk-view.notify-player", true)) {
            plugin.getMessageManager().send(player, "afk-view-reduced");
        }
    }

    private void restore(Player player) {
        Saved saved = reduced.remove(player.getUniqueId());
        if (saved == null) {
            return;
        }
        player.setViewDistance(saved.view());
        player.setSendViewDistance(saved.send());
        player.setSimulationDistance(saved.simulation());
        if (plugin.getConfigManager().getConfig().getBoolean("afk-view.notify-player", true)) {
            plugin.getMessageManager().send(player, "afk-view-restored");
        }
    }

    public void restoreAll() {
        for (UUID id : new java.util.ArrayList<>(reduced.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                restore(player);
            } else {
                reduced.remove(id);
            }
        }
    }

    // Main thread only.
    private void active(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (!reduced.isEmpty() && reduced.containsKey(player.getUniqueId())) {
            restore(player);
        }
    }

    // Hot path: fires constantly. Only real movement or looking around counts.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch()
                || from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            active(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        active(event.getPlayer());
    }

    // Async: just note the time here; restoring happens on the main thread.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastActivity.put(id, System.currentTimeMillis());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null && reduced.containsKey(id)) {
                restore(player);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastActivity.remove(event.getPlayer().getUniqueId());
        reduced.remove(event.getPlayer().getUniqueId());
    }

    // Test seam.
    void setLastActivity(UUID id, long millis) {
        lastActivity.put(id, millis);
    }
}
