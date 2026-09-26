package net.dimensiontoggle.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.dimensiontoggle.DimensionToggle;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class UpdateChecker implements Listener {

    private static final String MODRINTH_PROJECT_SLUG = "dimension-toggle";

    private final DimensionToggle plugin;
    private BukkitTask task;

    // One client for the plugin's lifetime: every HttpClient owns its own
    // selector thread + connection pool, and the old code built a new one
    // per check and never closed it.
    private volatile HttpClient client;
    private volatile boolean shutDown;

    private volatile String latestKnownVersion = null;

    private volatile int versionsBehind = -1;

    private volatile String lastBroadcastVersion = null;

    public UpdateChecker(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        if (!plugin.getConfigManager().getConfig().getBoolean("update-checker.enabled", true)) {
            plugin.getLogger().info("Update checker: disabled in config.yml (update-checker.enabled: false).");
            return;
        }

        long intervalMinutes = Math.max(5, plugin.getConfigManager().getConfig()
                .getLong("update-checker.check-interval-minutes", 60));
        long intervalTicks = intervalMinutes * 60L * 20L;

        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> check(MODRINTH_PROJECT_SLUG), 100L, intervalTicks);
    }

    public void checkNow() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> check(MODRINTH_PROJECT_SLUG));
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // onDisable only: also aborts an in-flight request instead of letting it
    // run (up to the 10s timeout) after the plugin is gone.
    public void shutdown() {
        stop();
        shutDown = true;
        HttpClient current = client;
        client = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    private HttpClient client() {
        HttpClient current = client;
        if (current == null) {
            current = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            client = current;
        }
        return current;
    }

    // synchronized: the periodic check and a manual /dt update-check could
    // otherwise run at the same time and both announce the same version.
    private synchronized void check(String slug) {
        if (shutDown) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.modrinth.com/v2/project/" + slug + "/version"))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "StonePlugins/DimensionToggle update-checker")
                    .GET()
                    .build();

            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().warning("Update checker: Modrinth responded with status " + response.statusCode()
                        + " for project '" + slug + "'.");
                return;
            }

            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
            if (versions.isEmpty()) {
                plugin.getLogger().info("Update checker: project '" + slug + "' found, but no version uploaded yet.");
                return;
            }

            String newest = versions.get(0).getAsJsonObject().get("version_number").getAsString();
            String current = plugin.getPluginMeta().getVersion();

            if (isNewer(newest, current)) {
                latestKnownVersion = newest;
                versionsBehind = countVersionsBehind(versions, current);

                if (!newest.equals(lastBroadcastVersion)) {
                    lastBroadcastVersion = newest;
                    logToConsole(newest, current);
                    Bukkit.getScheduler().runTask(plugin, () -> notifyOnlineEligiblePlayers(newest, current));
                }
            } else {
                latestKnownVersion = null;
                versionsBehind = -1;
                plugin.getLogger().info("No new version available (running " + current + ").");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Update checker: check failed (" + e.getClass().getSimpleName()
                    + ": " + e.getMessage() + ")");
        }
    }

    private void logToConsole(String newest, String current) {
        String behind = versionsBehind < 0 ? "an unknown number of versions" : versionsBehind + " version(s)";
        plugin.getLogger().info("A new version of DimensionToggle is available: " + newest
                + " (you're on " + current + ", " + behind + " behind). Check Modrinth to update.");
    }

    private int countVersionsBehind(JsonArray versions, String current) {
        for (int i = 0; i < versions.size(); i++) {
            String versionNumber = versions.get(i).getAsJsonObject().get("version_number").getAsString();
            if (versionNumber.equals(current)) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String newest = latestKnownVersion;
        if (newest == null || !isEligible(player)) {
            return;
        }
        notifyPlayer(player, newest, plugin.getPluginMeta().getVersion());
    }

    private boolean isEligible(Player player) {
        return player.isOp() || player.hasPermission("dimensiontoggle.admin");
    }

    // Resolve the message once and reuse the parsed Component for every
    // recipient, instead of re-parsing the identical text per online player.
    private void notifyOnlineEligiblePlayers(String newest, String current) {
        MessageManager messages = plugin.getMessageManager();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("version", newest);
        placeholders.put("current", current);
        placeholders.put("behind", describeVersionsBehind());

        Component message = messages.parseWithPlaceholders(messages.getPrefix() + messages.get("update-available"), placeholders);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isEligible(player)) {
                player.sendMessage(message);
            }
        }
    }

    private void notifyPlayer(Player player, String newest, String current) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("version", newest);
        placeholders.put("current", current);
        placeholders.put("behind", describeVersionsBehind());
        plugin.getMessageManager().send(player, "update-available", placeholders);
    }

    private String describeVersionsBehind() {
        int behind = versionsBehind;
        if (behind < 0) {
            return plugin.getMessageManager().get("update-versions-behind-unknown");
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("count", String.valueOf(behind));
        return plugin.getMessageManager().getFormatted("update-versions-behind", placeholders);
    }

    private boolean isNewer(String remote, String current) {
        try {
            String[] remoteParts = remote.split("\\.");
            String[] currentParts = current.split("\\.");
            int length = Math.max(remoteParts.length, currentParts.length);

            for (int i = 0; i < length; i++) {
                int r = i < remoteParts.length ? parsePart(remoteParts[i]) : 0;
                int c = i < currentParts.length ? parsePart(currentParts[i]) : 0;
                if (r != c) {
                    return r > c;
                }
            }
            return false;
        } catch (Exception e) {

            return false;
        }
    }

    private int parsePart(String part) {
        StringBuilder digits = new StringBuilder();
        for (char c : part.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        return digits.isEmpty() ? 0 : Integer.parseInt(digits.toString());
    }
}
