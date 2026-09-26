package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScheduleManager {

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("H:mm[:ss]");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final DimensionToggle plugin;
    private BukkitTask task;
    private Clock clock = Clock.systemDefaultZone();

    private final Map<ToggleDimension, Set<Integer>> firedOpenWarnings = new EnumMap<>(ToggleDimension.class);
    private final Map<ToggleDimension, Set<Integer>> firedCloseWarnings = new EnumMap<>(ToggleDimension.class);
    private final Map<String, Long> lastRawSecondsUntil = new HashMap<>();
    // "path=value" pairs already reported as invalid - the tick runs every
    // second, so without this a single typo would spam the console forever.
    private final Set<String> warnedInvalidTimes = new HashSet<>();
    private LocalDate lastResetDate = LocalDate.now(clock);

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

    // Test seam: lets tests drive the schedule with a controllable clock.
    void setClock(Clock clock) {
        this.clock = clock;
        this.lastResetDate = LocalDate.now(clock);
    }

    void tick() {
        LocalDate today = LocalDate.now(clock);
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
            // Feature is off - forget any in-progress crossing tracking so a later
            // re-enable starts clean instead of comparing against stale data.
            lastRawSecondsUntil.remove("schedule:" + dimension.getKey() + ":open");
            lastRawSecondsUntil.remove("schedule:" + dimension.getKey() + ":close");
            return;
        }

        checkTarget(dimension, section, "open-time", true, firedOpenWarnings.get(dimension));
        checkTarget(dimension, section, "close-time", false, firedCloseWarnings.get(dimension));
    }

    private void checkTarget(ToggleDimension dimension, ConfigurationSection section, String timeKey,
                              boolean opening, Set<Integer> fired) {
        Object rawTime = section.get(timeKey);
        String bossBarKey = "schedule:" + dimension.getKey() + ":" + (opening ? "open" : "close");

        if (rawTime == null || rawTime.toString().isBlank()) {
            plugin.getNotificationManager().removeCountdownBossBar(bossBarKey, Bukkit.getOnlinePlayers());
            lastRawSecondsUntil.remove(bossBarKey);
            return;
        }

        LocalTime target = parseTime(rawTime);
        if (target == null) {
            lastRawSecondsUntil.remove(bossBarKey);
            String path = section.getCurrentPath() + "." + timeKey;
            if (warnedInvalidTimes.add(path + "=" + rawTime)) {
                plugin.getLogger().warning("Invalid time '" + rawTime + "' at " + path
                        + " in config.yml (expected HH:mm, e.g. \"22:00\") - this schedule entry is ignored.");
            }
            return;
        }
        String timeStr = DISPLAY_FORMAT.format(target);

        LocalTime now = LocalTime.now(clock).withNano(0);
        // Unwrapped difference: positive while today's target is still ahead,
        // <= 0 the instant "now" reaches or passes it.
        long rawSecondsUntil = Duration.between(now, target).getSeconds();

        // The tick only runs ~once/second; on a lag spike more than a second
        // of real time can pass between two runs, so rawSecondsUntil can jump
        // straight from e.g. 2 to -3 and skip the exact value 0 entirely. A
        // plain "== 0" check would then silently miss that day's open/close.
        // Comparing against the previous tick's sign catches the crossing
        // regardless of how many seconds were skipped over.
        Long previousRaw = lastRawSecondsUntil.put(bossBarKey, rawSecondsUntil);
        boolean crossedTarget = previousRaw != null && previousRaw > 0 && rawSecondsUntil <= 0;

        long secondsUntil = rawSecondsUntil < 0 ? rawSecondsUntil + 86400 : rawSecondsUntil;

        boolean countdownEnabled = section.getBoolean("countdown-enabled", true);
        List<Integer> warnings = countdownEnabled ? section.getIntegerList("countdown-warnings") : List.of();
        int maxWarning = warnings.isEmpty() ? 0 : Collections.max(warnings);

        if (crossedTarget) {
            plugin.getNotificationManager().removeCountdownBossBar(bossBarKey, Bukkit.getOnlinePlayers());

            boolean changed = plugin.getDimensionManager().isEnabled(dimension) != opening;
            if (changed && opening && plugin.getDimensionManager().isLockdownActive()) {
                // A lockdown is an explicit emergency stop - a timer must not
                // silently lift it by reopening a dimension behind the admin's back.
                plugin.getLogManager().log(null, "SCHEDULE OPEN SKIPPED", dimension,
                        "Lockdown active at " + timeStr);
                changed = false;
            }
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

    // Bukkit's YAML parser follows YAML 1.1, where an unquoted time such as
    // `close-time: 22:00` is a base-60 integer (22*60+0 = 1320), not a string.
    // Plain LocalTime.parse() on "1320" fails, so a perfectly natural config
    // line used to silently disable that schedule entry. Accept both forms,
    // plus single-digit hours ("8:00"), which LocalTime.parse() rejects too.
    public static LocalTime parseTime(Object raw) {
        if (raw instanceof Integer || raw instanceof Long) {
            long value = ((Number) raw).longValue();
            if (value < 0) {
                return null;
            }
            // Two-part H:mm yields H*60+m (< 1440); three-part H:mm:ss yields
            // H*3600+m*60+s, which is always >= 3600 because a leading-zero
            // hour ("0:05:00") isn't sexagesimal and stays a string. Anything
            // in between can only be an out-of-range H:mm such as "25:00",
            // except 24:00, which (like the quoted form) means midnight.
            if (value < 24 * 60) {
                return LocalTime.of((int) (value / 60), (int) (value % 60));
            }
            if (value == 24 * 60) {
                return LocalTime.MIDNIGHT;
            }
            if (value >= 3600 && value < 24 * 3600) {
                return LocalTime.ofSecondOfDay(value);
            }
            return null;
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return LocalTime.parse(trimmed, INPUT_FORMAT);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
        return null;
    }

    public static String formatTime(Object raw) {
        if (raw == null) {
            return "?";
        }
        LocalTime time = parseTime(raw);
        return time == null ? raw.toString() : DISPLAY_FORMAT.format(time);
    }
}
