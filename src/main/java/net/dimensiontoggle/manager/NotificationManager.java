package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.MessageDisplayType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;

public class NotificationManager {

    private final DimensionToggle plugin;
    private final MessageManager messages;
    private final Map<String, BossBar> countdownBossBars = new java.util.HashMap<>();

    public NotificationManager(DimensionToggle plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
    }

    public void notifyActivate(Player player, String dimensionKey) {
        notify(player, dimensionKey, "activate");
    }

    public void notifyDeactivate(Player player, String dimensionKey) {
        notify(player, dimensionKey, "deactivate");
    }

    // At 250+ players a single /dt nether on|off used to call notify() once
    // per online player, which re-read the config section, re-resolved the
    // channel, and re-ran MiniMessage parsing 250 times for the exact same
    // text. This variant resolves everything ONCE and reuses the parsed
    // Component/Title/BossBar settings for every recipient - the only part
    // that still has to happen per player is the actual packet send, which
    // is unavoidable (each client needs its own title/actionbar/bossbar packet).
    public void notifyAll(java.util.Collection<? extends Player> targets, String dimensionKey, String state) {
        ConfigurationSection section = plugin.getConfigManager().getConfig()
                .getConfigurationSection(dimensionKey + "." + state);
        if (section == null || targets.isEmpty()) {
            return;
        }

        MessageDisplayType type = MessageDisplayType.fromConfig(section.getString("notification"), MessageDisplayType.NONE);

        switch (type) {
            case CHAT -> {
                String raw = section.getString("chat", null);
                if (raw == null || raw.isBlank()) {
                    return;
                }
                Component message = messages.parseWithPlaceholders(messages.getPrefix() + raw, null);
                for (Player player : targets) {
                    player.sendMessage(message);
                }
            }
            case ACTIONBAR -> {
                String raw = section.getString("actionbar", null);
                if (raw == null || raw.isBlank()) {
                    return;
                }
                Component message = messages.parse(raw);
                for (Player player : targets) {
                    player.sendActionBar(message);
                }
            }
            case TITLE -> {
                Component titleText = messages.parse(section.getString("title", ""));
                Component subtitleText = messages.parse(section.getString("subtitle", ""));
                Title.Times times = Title.Times.times(
                        Duration.ofMillis(section.getLong("fadein", 10) * 50L),
                        Duration.ofMillis(section.getLong("stay", 60) * 50L),
                        Duration.ofMillis(section.getLong("fadeout", 10) * 50L)
                );
                Title title = Title.title(titleText, subtitleText, times);
                for (Player player : targets) {
                    player.showTitle(title);
                }
            }
            case BOSSBAR -> {
                String raw = section.getString("bossbar", "");
                BossBar.Color color = parseColor(section.getString("bossbar-color", "WHITE"));
                BossBar.Overlay style = parseStyle(section.getString("bossbar-style", "SOLID"));
                int duration = section.getInt("bossbar-duration", 5);
                showTemporaryBossBarToAll(targets, messages.parseWithPlaceholders(raw, null), color, style, duration);
            }
            case NONE -> {
            }
        }
    }

    private void notify(Player player, String dimensionKey, String state) {
        ConfigurationSection section = plugin.getConfigManager().getConfig()
                .getConfigurationSection(dimensionKey + "." + state);
        if (section == null) {
            return;
        }

        MessageDisplayType type = MessageDisplayType.fromConfig(section.getString("notification"), MessageDisplayType.NONE);

        switch (type) {
            case CHAT -> {
                String raw = section.getString("chat", null);
                if (raw != null && !raw.isBlank()) {
                    player.sendMessage(messages.parseWithPlaceholders(messages.getPrefix() + raw, null));
                }
            }
            case ACTIONBAR -> {
                String raw = section.getString("actionbar", null);
                if (raw != null && !raw.isBlank()) {
                    player.sendActionBar(messages.parse(raw));
                }
            }
            case TITLE -> {
                Component titleText = messages.parse(section.getString("title", ""));
                Component subtitleText = messages.parse(section.getString("subtitle", ""));
                Title.Times times = Title.Times.times(
                        Duration.ofMillis(section.getLong("fadein", 10) * 50L),
                        Duration.ofMillis(section.getLong("stay", 60) * 50L),
                        Duration.ofMillis(section.getLong("fadeout", 10) * 50L)
                );
                player.showTitle(Title.title(titleText, subtitleText, times));
            }
            case BOSSBAR -> {
                String raw = section.getString("bossbar", "");
                BossBar.Color color = parseColor(section.getString("bossbar-color", "WHITE"));
                BossBar.Overlay style = parseStyle(section.getString("bossbar-style", "SOLID"));
                int duration = section.getInt("bossbar-duration", 5);
                showTemporaryBossBar(player, messages.parseWithPlaceholders(raw, null), color, style, duration);
            }
            case NONE -> {

            }
        }
    }

    // Same idea as notifyAll() above, but for the lockdown/softlock/maintenance/
    // schedule broadcast path (broadcastCustom). Used wherever the caller needs
    // to reach every online player at once instead of a single target.
    public void broadcastCustomToAll(java.util.Collection<? extends Player> targets, String configSection,
                                      String messageKey, Map<String, String> placeholders) {
        broadcastCustomToAll(targets, configSection, messageKey, placeholders, true);
    }

    public void broadcastCustomToAll(java.util.Collection<? extends Player> targets, String configSection,
                                      String messageKey, Map<String, String> placeholders, boolean includeBossbar) {
        ConfigurationSection settingsRoot = plugin.getConfigManager().getConfig().getConfigurationSection(configSection);
        if (settingsRoot == null || targets.isEmpty()) {
            return;
        }

        ConfigurationSection messagesRoot = settingsRoot.getConfigurationSection("messages");
        ConfigurationSection leaf = messagesRoot == null ? null : messagesRoot.getConfigurationSection(messageKey);
        if (leaf == null) {
            return;
        }

        MessageDisplayType type = MessageDisplayType.fromConfig(settingsRoot.getString("notification"), MessageDisplayType.CHAT);
        if (type == MessageDisplayType.BOSSBAR && !includeBossbar) {
            return;
        }

        switch (type) {
            case CHAT -> {
                String chatText = leaf.getString("chat", null);
                if (chatText == null || chatText.isBlank()) {
                    return;
                }
                Component message = messages.parseWithPlaceholders(messages.getPrefix() + chatText, placeholders);
                for (Player player : targets) {
                    player.sendMessage(message);
                }
            }
            case ACTIONBAR -> {
                String raw = leaf.getString("actionbar", null);
                if (raw == null || raw.isBlank()) {
                    return;
                }
                Component message = messages.parseWithPlaceholders(raw, placeholders);
                for (Player player : targets) {
                    player.sendActionBar(message);
                }
            }
            case TITLE -> {
                String titleRaw = leaf.getString("title", "");
                String subtitleRaw = leaf.getString("subtitle", "");
                ConfigurationSection timing = settingsRoot.getConfigurationSection("title");
                Title.Times times = Title.Times.times(
                        Duration.ofMillis((timing == null ? 10 : timing.getLong("fadein", 10)) * 50L),
                        Duration.ofMillis((timing == null ? 60 : timing.getLong("stay", 60)) * 50L),
                        Duration.ofMillis((timing == null ? 10 : timing.getLong("fadeout", 10)) * 50L)
                );
                Title title = Title.title(
                        messages.parseWithPlaceholders(titleRaw, placeholders),
                        messages.parseWithPlaceholders(subtitleRaw, placeholders),
                        times);
                for (Player player : targets) {
                    player.showTitle(title);
                }
            }
            case BOSSBAR -> {
                String raw = leaf.getString("bossbar", leaf.getString("chat", ""));
                ConfigurationSection bossbarConfig = settingsRoot.getConfigurationSection("bossbar");
                BossBar.Color color = parseColor(bossbarConfig == null ? "WHITE" : bossbarConfig.getString("color", "WHITE"));
                BossBar.Overlay style = parseStyle(bossbarConfig == null ? "SOLID" : bossbarConfig.getString("style", "SOLID"));
                int duration = bossbarConfig == null ? 5 : bossbarConfig.getInt("duration", 5);
                showTemporaryBossBarToAll(targets, messages.parseWithPlaceholders(raw, placeholders), color, style, duration);
            }
            case NONE -> {
            }
        }
    }

    public void broadcastCustom(CommandSender target, String configSection, String messageKey,
                                 Map<String, String> placeholders) {
        broadcastCustom(target, configSection, messageKey, placeholders, true);
    }

    public void broadcastCustom(CommandSender target, String configSection, String messageKey,
                                 Map<String, String> placeholders, boolean includeBossbar) {
        ConfigurationSection settingsRoot = plugin.getConfigManager().getConfig().getConfigurationSection(configSection);
        if (settingsRoot == null) {
            return;
        }

        ConfigurationSection messagesRoot = settingsRoot.getConfigurationSection("messages");
        ConfigurationSection leaf = messagesRoot == null ? null : messagesRoot.getConfigurationSection(messageKey);
        if (leaf == null) {
            return;
        }

        MessageDisplayType type = MessageDisplayType.fromConfig(settingsRoot.getString("notification"), MessageDisplayType.CHAT);
        if (type == MessageDisplayType.BOSSBAR && !includeBossbar) {
            return;
        }

        switch (type) {
            case CHAT -> {
                String chatText = leaf.getString("chat", null);
                if (chatText != null && !chatText.isBlank()) {
                    target.sendMessage(messages.parseWithPlaceholders(messages.getPrefix() + chatText, placeholders));
                }
            }
            case ACTIONBAR -> {
                if (target instanceof Player player) {
                    String raw = leaf.getString("actionbar", null);
                    if (raw != null && !raw.isBlank()) {
                        player.sendActionBar(messages.parseWithPlaceholders(raw, placeholders));
                    }
                }
            }
            case TITLE -> {
                if (target instanceof Player player) {
                    String titleRaw = leaf.getString("title", "");
                    String subtitleRaw = leaf.getString("subtitle", "");
                    ConfigurationSection timing = settingsRoot.getConfigurationSection("title");

                    Title.Times times = Title.Times.times(
                            Duration.ofMillis((timing == null ? 10 : timing.getLong("fadein", 10)) * 50L),
                            Duration.ofMillis((timing == null ? 60 : timing.getLong("stay", 60)) * 50L),
                            Duration.ofMillis((timing == null ? 10 : timing.getLong("fadeout", 10)) * 50L)
                    );

                    player.showTitle(Title.title(
                            messages.parseWithPlaceholders(titleRaw, placeholders),
                            messages.parseWithPlaceholders(subtitleRaw, placeholders),
                            times));
                }
            }
            case BOSSBAR -> {
                if (target instanceof Player player) {
                    String raw = leaf.getString("bossbar", leaf.getString("chat", ""));
                    ConfigurationSection bossbarConfig = settingsRoot.getConfigurationSection("bossbar");

                    BossBar.Color color = parseColor(bossbarConfig == null ? "WHITE" : bossbarConfig.getString("color", "WHITE"));
                    BossBar.Overlay style = parseStyle(bossbarConfig == null ? "SOLID" : bossbarConfig.getString("style", "SOLID"));
                    int duration = bossbarConfig == null ? 5 : bossbarConfig.getInt("duration", 5);

                    showTemporaryBossBar(player, messages.parseWithPlaceholders(raw, placeholders), color, style, duration);
                }
            }
            case NONE -> {

            }
        }
    }

    public boolean usesBossBar(String configSection) {
        ConfigurationSection settingsRoot = plugin.getConfigManager().getConfig().getConfigurationSection(configSection);
        if (settingsRoot == null) {
            return false;
        }
        return MessageDisplayType.fromConfig(settingsRoot.getString("notification"), MessageDisplayType.CHAT) == MessageDisplayType.BOSSBAR;
    }

    private void showTemporaryBossBar(Player player, Component message, BossBar.Color color,
                                       BossBar.Overlay style, int durationSeconds) {
        BossBar bossBar = BossBar.bossBar(message, 1.0f, color, style);
        player.showBossBar(bossBar);

        new BukkitRunnable() {
            @Override
            public void run() {
                player.hideBossBar(bossBar);
            }
        }.runTaskLater(plugin, Math.max(1, durationSeconds) * 20L);
    }

    // Adventure's BossBar natively supports multiple viewers on one shared
    // instance, so a broadcast to 250 players needs exactly one BossBar
    // object and one scheduled hide task - not 250 of each, which is what
    // calling showTemporaryBossBar() in a per-player loop would produce.
    private void showTemporaryBossBarToAll(java.util.Collection<? extends Player> targets, Component message,
                                            BossBar.Color color, BossBar.Overlay style, int durationSeconds) {
        BossBar bossBar = BossBar.bossBar(message, 1.0f, color, style);
        for (Player player : targets) {
            player.showBossBar(bossBar);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : targets) {
                    player.hideBossBar(bossBar);
                }
            }
        }.runTaskLater(plugin, Math.max(1, durationSeconds) * 20L);
    }

    public void updateCountdownBossBar(String key, java.util.Collection<? extends Player> targets,
                                        String configSection, String rawText, Map<String, String> placeholders,
                                        int secondsLeft, int totalSeconds) {
        Component text = messages.parseWithPlaceholders(rawText, placeholders);
        float progress = totalSeconds <= 0 ? 0f : Math.max(0f, Math.min(1f, secondsLeft / (float) totalSeconds));

        BossBar bar = countdownBossBars.get(key);
        if (bar == null) {
            ConfigurationSection bossbarConfig = plugin.getConfigManager().getConfig()
                    .getConfigurationSection(configSection + ".bossbar");
            BossBar.Color color = parseColor(bossbarConfig == null ? "WHITE" : bossbarConfig.getString("color", "WHITE"));
            BossBar.Overlay style = parseStyle(bossbarConfig == null ? "SOLID" : bossbarConfig.getString("style", "SOLID"));
            bar = BossBar.bossBar(text, progress, color, style);
            countdownBossBars.put(key, bar);
        } else {
            bar.name(text);
            bar.progress(progress);
        }

        for (Player player : targets) {
            player.showBossBar(bar);
        }
    }

    public void removeCountdownBossBar(String key, java.util.Collection<? extends Player> targets) {
        BossBar bar = countdownBossBars.remove(key);
        if (bar == null) {
            return;
        }
        for (Player player : targets) {
            player.hideBossBar(bar);
        }
    }

    private static final Map<String, String> COLOR_ALIASES = Map.of(
            "GOLD", "YELLOW",
            "ORANGE", "YELLOW",
            "MAGENTA", "PURPLE",
            "VIOLET", "PURPLE",
            "CYAN", "BLUE",
            "GRAY", "WHITE",
            "GREY", "WHITE"
    );

    private static final Map<String, String> STYLE_ALIASES = Map.of(
            "SOLID", "PROGRESS",
            "BAR", "PROGRESS",
            "FULL", "PROGRESS",
            "NONE", "PROGRESS",
            "SEGMENTED", "NOTCHED_10",
            "SEGMENTS", "NOTCHED_10"
    );

    private BossBar.Color parseColor(String raw) {
        String normalized = raw.toUpperCase();
        try {
            return BossBar.Color.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            String alias = COLOR_ALIASES.get(normalized);
            if (alias != null) {
                return BossBar.Color.valueOf(alias);
            }
            plugin.getLogger().log(Level.WARNING, "Invalid BossBar color: " + raw
                    + " (valid: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE)");
            return BossBar.Color.WHITE;
        }
    }

    private BossBar.Overlay parseStyle(String raw) {
        String normalized = raw.toUpperCase();
        try {
            return BossBar.Overlay.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            String alias = STYLE_ALIASES.get(normalized);
            if (alias != null) {
                return BossBar.Overlay.valueOf(alias);
            }
            plugin.getLogger().log(Level.WARNING, "Invalid BossBar style: " + raw
                    + " (valid: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20)");
            return BossBar.Overlay.PROGRESS;
        }
    }
}
