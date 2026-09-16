package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MaintenanceManager {

    private final DimensionToggle plugin;
    private final Map<ToggleDimension, BukkitTask> activeCountdowns = new EnumMap<>(ToggleDimension.class);
    private final Map<ToggleDimension, Integer> remainingSeconds = new EnumMap<>(ToggleDimension.class);

    public MaintenanceManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public boolean isPending(ToggleDimension dimension) {
        return activeCountdowns.containsKey(dimension);
    }

    public int getRemainingSeconds(ToggleDimension dimension) {
        return remainingSeconds.getOrDefault(dimension, 0);
    }

    public boolean start(ToggleDimension dimension, int totalSeconds, CommandSender initiator) {
        if (isPending(dimension)) {
            return false;
        }

        totalSeconds = Math.max(1, totalSeconds);
        remainingSeconds.put(dimension, totalSeconds);
        int total = totalSeconds;
        String bossBarKey = "maintenance:" + dimension.getKey();

        List<Integer> warnings = plugin.getConfigManager().getConfig()
                .getIntegerList("maintenance.countdown.warnings");

        broadcast(dimension, "scheduled", Map.of("duration", formatSeconds(total)), false);
        updateBossBar(dimension, bossBarKey, total, total);
        plugin.getLogManager().log(initiator, "MAINTENANCE SCHEDULED", dimension, formatSeconds(total) + " countdown");

        AtomicInteger secondsLeft = new AtomicInteger(totalSeconds);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int current = secondsLeft.decrementAndGet();
            remainingSeconds.put(dimension, Math.max(current, 0));

            updateBossBar(dimension, bossBarKey, Math.max(current, 0), total);

            if (current <= 0) {
                plugin.getNotificationManager().removeCountdownBossBar(bossBarKey, bossBarTargets(dimension));
                execute(dimension, initiator);
                BukkitTask self = activeCountdowns.remove(dimension);
                remainingSeconds.remove(dimension);
                if (self != null) {
                    self.cancel();
                }
                return;
            }

            if (warnings.contains(current)) {
                broadcast(dimension, "warning", Map.of("time", formatSeconds(current)), false);
                plugin.getSoundManager().playMaintenanceWarningForAll(
                        plugin.getDimensionManager().getPlayersInDimension(dimension));
            }
        }, 20L, 20L);

        activeCountdowns.put(dimension, task);
        return true;
    }

    public enum CancelResult {

        NOTHING_TO_DO,

        COUNTDOWN_CANCELLED,

        MAINTENANCE_ENDED
    }

    public CancelResult cancel(ToggleDimension dimension, CommandSender initiator) {
        BukkitTask task = activeCountdowns.remove(dimension);
        remainingSeconds.remove(dimension);

        if (task != null) {
            task.cancel();
            plugin.getNotificationManager().removeCountdownBossBar("maintenance:" + dimension.getKey(), bossBarTargets(dimension));
            broadcast(dimension, "cancelled", Map.of(), true);
            plugin.getLogManager().log(initiator, "MAINTENANCE CANCELLED", dimension, null);
            return CancelResult.COUNTDOWN_CANCELLED;
        }

        if (!plugin.getDimensionManager().isEnabled(dimension)) {
            plugin.getDimensionManager().setEnabledSilently(dimension, true, initiator);
            broadcast(dimension, "ended", Map.of(), true);
            plugin.getLogManager().log(initiator, "MAINTENANCE ENDED", dimension, "Dimension re-enabled");
            return CancelResult.MAINTENANCE_ENDED;
        }

        return CancelResult.NOTHING_TO_DO;
    }

    private void execute(ToggleDimension dimension, CommandSender initiator) {
        String actionCommand = plugin.getConfigManager().getConfig().getString("maintenance.action-command", "");
        boolean kickToSpawn = plugin.getConfigManager().getConfig().getBoolean("maintenance.kick-to-spawn", true);

        Map<String, String> teleportPlaceholders = Map.of("dimension", plugin.getMessageManager().getDimensionName(dimension.getKey()));
        List<Player> affectedPlayers = plugin.getDimensionManager().getPlayersInDimension(dimension);
        plugin.getMessageManager().sendToAll(affectedPlayers, "maintenance-teleported", teleportPlaceholders);
        plugin.getSoundManager().playMaintenanceExecuteForAll(affectedPlayers);

        if (kickToSpawn || (actionCommand != null && !actionCommand.isBlank())) {
            plugin.getDimensionManager().removePlayersFromDimension(dimension, actionCommand);
        }

        plugin.getDimensionManager().setEnabledSilently(dimension, false, initiator);
        broadcast(dimension, "executed", Map.of(), true);
        plugin.getLogManager().log(initiator, "MAINTENANCE EXECUTED", dimension, "Dimension disabled");
    }

    private void updateBossBar(ToggleDimension dimension, String bossBarKey, int secondsLeft, int totalSeconds) {
        if (!plugin.getNotificationManager().usesBossBar("maintenance")) {
            return;
        }

        ConfigurationSection warningLeaf = plugin.getConfigManager().getConfig()
                .getConfigurationSection("maintenance.messages.warning");
        String text = warningLeaf == null ? "" : warningLeaf.getString("bossbar", warningLeaf.getString("chat", ""));
        if (text == null || text.isBlank()) {
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("dimension", plugin.getMessageManager().getDimensionName(dimension.getKey()));
        placeholders.put("time", formatSeconds(secondsLeft));

        plugin.getNotificationManager().updateCountdownBossBar(bossBarKey, bossBarTargets(dimension),
                "maintenance", text, placeholders, secondsLeft, totalSeconds);
    }

    private Collection<? extends Player> bossBarTargets(ToggleDimension dimension) {
        String scope = plugin.getConfigManager().getConfig().getString("maintenance.broadcast-scope", "server");
        return "dimension".equalsIgnoreCase(scope)
                ? plugin.getDimensionManager().getPlayersInDimension(dimension)
                : Bukkit.getOnlinePlayers();
    }

    private void broadcast(ToggleDimension dimension, String messageKeyPrefix, Map<String, String> extraPlaceholders,
                            boolean includeBossbar) {
        Map<String, String> placeholders = new HashMap<>(extraPlaceholders);
        placeholders.put("dimension", plugin.getMessageManager().getDimensionName(dimension.getKey()));

        // Was: broadcastCustom() per recipient in this loop - identical
        // text re-parsed once per player. At "server" scope with 250
        // players online that's 250x redundant parsing for one event.
        plugin.getNotificationManager().broadcastCustomToAll(bossBarTargets(dimension), "maintenance",
                messageKeyPrefix, placeholders, includeBossbar);
    }

    public static Integer parseDurationToSeconds(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim().toLowerCase();

        if (trimmed.matches("\\d+")) {
            return Integer.parseInt(trimmed) * 60;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)([hms])").matcher(trimmed);
        int total = 0;
        int matchedChars = 0;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            switch (matcher.group(2)) {
                case "h" -> total += value * 3600;
                case "m" -> total += value * 60;
                case "s" -> total += value;
            }
            matchedChars += matcher.group().length();
        }

        if (matchedChars == 0 || matchedChars != trimmed.length()) {
            return null;
        }
        return total;
    }

    private String formatSeconds(int totalSeconds) {
        return plugin.getMessageManager().formatDuration(totalSeconds);
    }
}
