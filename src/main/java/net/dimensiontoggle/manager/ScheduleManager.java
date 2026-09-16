package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScheduleManager {

    private final DimensionToggle plugin;
    private BukkitTask task;

    private final Map<ToggleDimension, Set<Integer>> firedOpenWarnings = new EnumMap<>(ToggleDimension.class);
    private final Map<ToggleDimension, Set<Integer>> firedCloseWarnings = new EnumMap<>(ToggleDimension.class);
    private LocalDate lastResetDate = LocalDate.now();

    public ScheduleManager(DimensionToggle plugin) {
        this.plugin = plugin;
        for (ToggleDimension dimension : ToggleDimension.values()) {
            firedOpenWarnings.put(dimension, new HashSet<>());
            firedCloseWarnings.put(dimension, new HashSet<>());
        }
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        LocalDate today = LocalDate.now();
        if (!today.equals(lastResetDate)) {
            firedOpenWarnings.values().forEach(Set::clear);
            firedCloseWarnings.values().forEach(Set::clear);
            lastResetDate = today;
        }

        for (ToggleDimension dimension : ToggleDimension.values()) {
            checkDimension(dimension);
        }
    }

    private void checkDimension(ToggleDimension dimension) {
        ConfigurationSection section = plugin.getConfigManager().getConfig()
                .getConfigurationSection("schedule." + dimension.getKey());
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }

        checkTarget(dimension, section, "open-time", true, firedOpenWarnings.get(dimension));
        checkTarget(dimension, section, "close-time", false, firedCloseWarnings.get(dimension));
    }

    private void checkTarget(ToggleDimension dimension, ConfigurationSection section, String timeKey,
                              boolean opening, Set<Integer> fired) {
        String timeStr = section.getString(timeKey, null);
        String bossBarKey = "schedule:" + dimension.getKey() + ":" + (opening ? "open" : "close");

        if (timeStr == null || timeStr.isBlank()) {
            plugin.getNotificationManager().removeCountdownBossBar(bossBarKey, Bukkit.getOnlinePlayers());
            return;
        }

        LocalTime target;
        try {
            target = LocalTime.parse(timeStr.trim());
        } catch (Exception ex) {
            return;
        }

        LocalTime now = LocalTime.now().withNano(0);
        long secondsUntil = Duration.between(now, target).getSeconds();
        if (secondsUntil < 0) {
            secondsUntil += 86400;
        }

        boolean countdownEnabled = section.getBoolean("countdown-enabled", true);
        List<Integer> warnings = countdownEnabled ? section.getIntegerList("countdown-warnings") : List.of();
        int maxWarning = warnings.isEmpty() ? 0 : Collections.max(warnings);

        if (secondsUntil == 0) {
            plugin.getNotificationManager().removeCountdownBossBar(bossBarKey, Bukkit.getOnlinePlayers());

            boolean changed = plugin.getDimensionManager().isEnabled(dimension) != opening;
            if (changed) {
                if (!opening) {
                    String actionCommand = plugin.getConfigManager().getConfig()
                            .getString("schedule.action-command", "");
                    plugin.getDimensionManager().removePlayersFromDimension(dimension, actionCommand);
                }
                plugin.getDimensionManager().setEnabledSilently(dimension, opening, null);
                broadcast(dimension, opening ? "opened" : "closed", Map.of(), true);
                plugin.getLogManager().log(null, opening ? "SCHEDULE OPENED" : "SCHEDULE CLOSED",
                        dimension, "Automatically at " + timeStr);
            }
            fired.clear();
            return;
        }

        int secondsUntilInt = (int) secondsUntil;

        if (countdownEnabled && secondsUntilInt <= maxWarning) {
            updateBossBar(dimension, bossBarKey, opening, secondsUntilInt, maxWarning);
        } else {
            plugin.getNotificationManager().removeCountdownBossBar(bossBarKey, Bukkit.getOnlinePlayers());
        }

        if (countdownEnabled && warnings.contains(secondsUntilInt) && fired.add(secondsUntilInt)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("time", formatSeconds(secondsUntilInt));
            placeholders.put("action", plugin.getMessageManager().get(opening ? "action-opens" : "action-closes"));
            broadcast(dimension, "warning", placeholders, false);
            plugin.getSoundManager().playScheduleWarningForAll(Bukkit.getOnlinePlayers());
        }
    }

    private void updateBossBar(ToggleDimension dimension, String bossBarKey, boolean opening,
                                int secondsLeft, int totalSeconds) {
        if (!plugin.getNotificationManager().usesBossBar("schedule")) {
            return;
        }

        ConfigurationSection warningLeaf = plugin.getConfigManager().getConfig()
                .getConfigurationSection("schedule.messages.warning");
        String text = warningLeaf == null ? "" : warningLeaf.getString("bossbar", warningLeaf.getString("chat", ""));
        if (text == null || text.isBlank()) {
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("dimension", plugin.getMessageManager().getDimensionName(dimension.getKey()));
        placeholders.put("time", formatSeconds(secondsLeft));
        placeholders.put("action", plugin.getMessageManager().get(opening ? "action-opens" : "action-closes"));

        plugin.getNotificationManager().updateCountdownBossBar(bossBarKey, Bukkit.getOnlinePlayers(),
                "schedule", text, placeholders, secondsLeft, totalSeconds);
    }

    private void broadcast(ToggleDimension dimension, String messageKeyPrefix, Map<String, String> extraPlaceholders,
                            boolean includeBossbar) {
        Map<String, String> placeholders = new HashMap<>(extraPlaceholders);
        placeholders.put("dimension", plugin.getMessageManager().getDimensionName(dimension.getKey()));

        plugin.getNotificationManager().broadcastCustomToAll(Bukkit.getOnlinePlayers(), "schedule",
                messageKeyPrefix, placeholders, includeBossbar);
    }

    private String formatSeconds(int totalSeconds) {
        return plugin.getMessageManager().formatDuration(totalSeconds);
    }
}
