package net.dimensiontoggle.command;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.config.ConfigValueWriter;
import net.dimensiontoggle.manager.MessageManager;
import net.dimensiontoggle.model.ToggleDimension;
import net.dimensiontoggle.performance.LagMapScanner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DimensionToggleCommand implements CommandExecutor, TabCompleter {

    private record CommandInfo(String name, int weight, String permission) {
    }

    private static final List<CommandInfo> COMMAND_REGISTRY = List.of(
            new CommandInfo("editor", 100, "dimensiontoggle.admin"),
            new CommandInfo("status", 95, "dimensiontoggle.status"),
            new CommandInfo("nether", 90, "dimensiontoggle.admin"),
            new CommandInfo("end", 85, "dimensiontoggle.admin"),
            new CommandInfo("softlock", 80, "dimensiontoggle.admin"),
            new CommandInfo("lockdown", 75, "dimensiontoggle.admin"),
            new CommandInfo("limit", 70, "dimensiontoggle.admin"),
            new CommandInfo("maintenance", 65, "dimensiontoggle.admin"),
            new CommandInfo("lagmap", 60, "dimensiontoggle.lagmap"),
            new CommandInfo("redstone", 58, "dimensiontoggle.redstone"),
            new CommandInfo("chunkloaders", 56, "dimensiontoggle.chunkloaders"),
            new CommandInfo("web", 54, "dimensiontoggle.performance"),
            new CommandInfo("help", 20, null),
            new CommandInfo("reload", 15, "dimensiontoggle.admin"),
            new CommandInfo("checkupdate", 10, "dimensiontoggle.admin")
    );

    private static final List<CommandInfo> COMMANDS_BY_WEIGHT = COMMAND_REGISTRY.stream()
            .sorted(Comparator.comparingInt(CommandInfo::weight).reversed())
            .toList();

    private static final List<String> STATES = List.of("on", "off");
    private static final List<String> MAINTENANCE_ACTIONS = List.of("10m", "30m", "1h", "5m", "cancel");

    private final DimensionToggle plugin;

    public DimensionToggleCommand(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            handleOverview(sender);
            return true;
        }

        MessageManager messages = plugin.getMessageManager();
        switch (args[0].toLowerCase()) {
            case "status" -> handleStatus(sender);
            case "reload" -> handleReload(sender);
            case "nether" -> handleToggle(sender, ToggleDimension.NETHER, args);
            case "end" -> handleToggle(sender, ToggleDimension.END, args);
            case "maintenance" -> handleMaintenance(sender, args);
            case "lockdown" -> handleLockdown(sender);
            case "softlock" -> handleSoftLock(sender, args);
            case "limit" -> handleLimit(sender, args);
            case "help" -> handleHelp(sender);
            case "editor" -> handleEditor(sender);
            case "checkupdate" -> handleCheckUpdate(sender);
            case "lagmap" -> handleLagmap(sender, args);
            case "redstone" -> handleReport(sender, args, "dimensiontoggle.redstone", "redstone");
            case "chunkloaders" -> handleReport(sender, args, "dimensiontoggle.chunkloaders", "chunkloaders");
            case "web" -> handleWeb(sender, args);
            case "tpchunk" -> handleTpChunk(sender, args);
            default -> messages.send(sender, "unknown-command");
        }

        return true;
    }

    private void handleOverview(CommandSender sender) {
        handleHelp(sender);
    }

    private boolean hasAdmin(CommandSender sender) {
        if (!sender.hasPermission("dimensiontoggle.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return false;
        }
        return true;
    }

    private void handleToggle(CommandSender sender, ToggleDimension dimension, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }

        MessageManager messages = plugin.getMessageManager();

        if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            messages.send(sender, "invalid-usage");
            return;
        }

        boolean enable = args[1].equalsIgnoreCase("on");
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("dimension", messages.getDimensionName(dimension.getKey()));

        boolean changed = plugin.getDimensionManager().setEnabled(dimension, enable, sender);

        if (!changed) {
            messages.send(sender, enable ? "dimension-already-enabled" : "dimension-already-disabled", placeholders);
            return;
        }

        messages.send(sender, enable ? "dimension-enabled" : "dimension-disabled", placeholders);
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("dimensiontoggle.status") && !sender.hasPermission("dimensiontoggle.admin")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return;
        }

        MessageManager messages = plugin.getMessageManager();
        messages.sendRaw(sender, "status-header");

        for (ToggleDimension dimension : ToggleDimension.values()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("status", statusText(messages, dimension));
            placeholders.put("players", String.valueOf(plugin.getDimensionManager().countPlayersInDimension(dimension)));
            messages.sendRaw(sender, "status-" + dimension.getKey(), placeholders);
        }

        messages.sendRaw(sender, "status-footer");
    }

    private String statusText(MessageManager messages, ToggleDimension dimension) {
        if (plugin.getMaintenanceManager().isPending(dimension)) {
            int seconds = plugin.getMaintenanceManager().getRemainingSeconds(dimension);
            return messages.get("status-maintenance-pending").replace("%seconds%", String.valueOf(seconds));
        }
        if (plugin.getDimensionManager().isLocked(dimension)) {
            return messages.get("status-locked");
        }
        return messages.get(plugin.getDimensionManager().isEnabled(dimension) ? "status-enabled" : "status-disabled");
    }

    private void handleReload(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return;
        }

        MessageManager messages = plugin.getMessageManager();

        try {
            plugin.getConfigManager().reloadAll();
            plugin.getGuiConfigManager().reload();
            plugin.getMessageManager().clearCache();
            plugin.getSoundManager().clearCache();
            // The only task that reads its settings (enabled, interval) once
            // at start - re-apply them so edits take effect without a restart.
            plugin.getUpdateChecker().start();
            plugin.getLagMapScanner().invalidate();
            plugin.startPerformanceFeatures();
            messages.send(sender, "reload-success");
        } catch (Exception ex) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("error", ex.getMessage() == null ? "unknown" : ex.getMessage());
            messages.send(sender, "reload-error", placeholders);
            plugin.getLogger().severe("Reload failed: " + ex.getMessage());
        }
    }

    private void handleMaintenance(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }

        MessageManager messages = plugin.getMessageManager();

        if (args.length < 3) {
            messages.send(sender, "maintenance-usage");
            return;
        }

        ToggleDimension dimension = ToggleDimension.fromString(args[1]);
        if (dimension == null) {
            messages.send(sender, "invalid-usage");
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("dimension", messages.getDimensionName(dimension.getKey()));

        if (args[2].equalsIgnoreCase("cancel")) {
            var result = plugin.getMaintenanceManager().cancel(dimension, sender);
            switch (result) {
                case COUNTDOWN_CANCELLED -> messages.send(sender, "maintenance-cancel-success", placeholders);
                case MAINTENANCE_ENDED -> messages.send(sender, "maintenance-ended-success", placeholders);
                case NOTHING_TO_DO -> messages.send(sender, "maintenance-none-active", placeholders);
            }
            return;
        }

        Integer totalSeconds = net.dimensiontoggle.manager.MaintenanceManager.parseDurationToSeconds(args[2]);
        if (totalSeconds == null || totalSeconds <= 0) {
            messages.send(sender, "maintenance-invalid-duration");
            return;
        }

        boolean started = plugin.getMaintenanceManager().start(dimension, totalSeconds, sender);
        if (!started) {
            messages.send(sender, "maintenance-already-active", placeholders);
            return;
        }

        placeholders.put("duration", plugin.getMessageManager().formatDuration(totalSeconds));
        messages.send(sender, "maintenance-start-success", placeholders);
    }

    private void handleSoftLock(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }

        MessageManager messages = plugin.getMessageManager();

        if (args.length < 2) {
            messages.send(sender, "softlock-usage");
            return;
        }

        ToggleDimension dimension = ToggleDimension.fromString(args[1]);
        if (dimension == null) {
            messages.send(sender, "invalid-usage");
            return;
        }

        plugin.getDimensionManager().toggleSoftLockWithBroadcast(dimension, sender);
    }

    private void handleLockdown(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return;
        }

        MessageManager messages = plugin.getMessageManager();
        boolean nowActive = plugin.getDimensionManager().toggleLockdownWithBroadcast(sender);

        if (!(sender instanceof org.bukkit.entity.Player)) {
            messages.send(sender, nowActive ? "lockdown-activated-console" : "lockdown-lifted-console");
        }
    }

    private void handleLimit(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return;
        }

        MessageManager messages = plugin.getMessageManager();

        if (args.length < 3) {
            messages.send(sender, "limit-usage");
            return;
        }

        ToggleDimension dimension = ToggleDimension.fromString(args[1]);
        if (dimension == null) {
            messages.send(sender, "invalid-usage");
            return;
        }

        int max;
        try {
            max = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            messages.send(sender, "limit-invalid-number");
            return;
        }

        if (max < 0) {
            messages.send(sender, "limit-invalid-number");
            return;
        }

        plugin.getConfigManager().getConfig().set("limits." + dimension.getKey() + ".max-players", max);
        plugin.getConfigManager().getConfig().set("limits." + dimension.getKey() + ".enabled", true);

        plugin.getConfigManager().persistConfigEdit("limits." + dimension.getKey(),
                path -> ConfigValueWriter.setLimit(path, dimension.getKey(), max));

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("dimension", messages.getDimensionName(dimension.getKey()));
        placeholders.put("limit", String.valueOf(max));
        messages.send(sender, "limit-set-success", placeholders);

        plugin.getLogManager().log(sender, "LIMIT CHANGED", dimension, "New limit: " + max);
    }

    private void handleCheckUpdate(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return;
        }
        plugin.getMessageManager().send(sender, "update-check-triggered");
        plugin.getUpdateChecker().checkNow();
    }

    private void handleEditor(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return;
        }
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            plugin.getMessageManager().send(sender, "players-only");
            return;
        }
        plugin.getGuiManager().openMain(player);
    }

    private void handleHelp(CommandSender sender) {
        MessageManager messages = plugin.getMessageManager();

        List<CommandInfo> visible = COMMANDS_BY_WEIGHT.stream()
                .filter(info -> info.permission() == null || sender.hasPermission(info.permission()))
                .toList();

        if (visible.isEmpty()) {
            messages.send(sender, "no-permission");
            return;
        }

        messages.sendRaw(sender, "help-header");
        for (CommandInfo info : visible) {
            messages.sendRaw(sender, "help-" + info.name());
            if (info.name().equals("maintenance")) {
                messages.sendRaw(sender, "help-maintenance-cancel");
            }
        }
        messages.sendRaw(sender, "help-footer");
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("dimensiontoggle.admin")) {
            return true;
        }
        plugin.getMessageManager().send(sender, "no-permission");
        return false;
    }

    private static final List<String> SORTS = List.of("score", "entities", "mobs", "items", "blocks");

    private static LagMapScanner.Sort parseSort(String input) {
        return switch (input.toLowerCase()) {
            case "entities" -> LagMapScanner.Sort.ENTITIES;
            case "mobs" -> LagMapScanner.Sort.MOBS;
            case "items" -> LagMapScanner.Sort.ITEMS;
            case "blocks" -> LagMapScanner.Sort.BLOCK_ENTITIES;
            default -> LagMapScanner.Sort.SCORE;
        };
    }

    // /dt lagmap          -> menu (players) / list (console)
    // /dt lagmap chat [sort]
    private void handleLagmap(CommandSender sender, String[] args) {
        if (!require(sender, "dimensiontoggle.lagmap")) {
            return;
        }
        boolean chat = args.length >= 2 && args[1].equalsIgnoreCase("chat");
        if (!chat && sender instanceof org.bukkit.entity.Player player) {
            plugin.getGuiManager().openLagmap(player);
            return;
        }
        LagMapScanner.Sort sort = args.length >= 3 ? parseSort(args[2]) : LagMapScanner.Sort.SCORE;
        plugin.getChunkReports().sendLagmap(sender, sort);
    }

    // /dt redstone | chunkloaders [chat]
    private void handleReport(CommandSender sender, String[] args, String permission, String report) {
        if (!require(sender, permission)) {
            return;
        }
        boolean chat = args.length >= 2 && args[1].equalsIgnoreCase("chat");
        if (!chat && sender instanceof org.bukkit.entity.Player player) {
            if (report.equals("redstone")) {
                plugin.getGuiManager().openRedstone(player);
            } else {
                plugin.getGuiManager().openChunkLoaders(player);
            }
            return;
        }
        if (report.equals("redstone")) {
            plugin.getChunkReports().sendRedstone(sender);
        } else {
            plugin.getChunkReports().sendChunkLoaders(sender);
        }
    }

    // Target of the clickable chunk lines.
    private void handleTpChunk(CommandSender sender, String[] args) {
        if (!(sender.hasPermission("dimensiontoggle.lagmap") || sender.hasPermission("dimensiontoggle.redstone")
                || sender.hasPermission("dimensiontoggle.chunkloaders") || sender.hasPermission("dimensiontoggle.admin"))) {
            plugin.getMessageManager().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            plugin.getMessageManager().send(sender, "players-only");
            return;
        }
        if (args.length < 4) {
            plugin.getMessageManager().send(sender, "invalid-usage");
            return;
        }
        int x;
        int z;
        try {
            x = Integer.parseInt(args[2]);
            z = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().send(sender, "invalid-usage");
            return;
        }
        if (!plugin.getChunkReports().teleport(player, args[1], x, z)) {
            plugin.getMessageManager().send(sender, "world-not-found", Map.of("world", args[1]));
            return;
        }
        plugin.getMessageManager().send(sender, "teleported-chunk",
                Map.of("world", args[1], "x", String.valueOf(x), "z", String.valueOf(z)));
    }

    // /dt web [reset-token]
    private void handleWeb(CommandSender sender, String[] args) {
        if (!require(sender, "dimensiontoggle.performance")) {
            return;
        }
        var web = plugin.getPerformanceWebServer();
        if (args.length >= 2 && args[1].equalsIgnoreCase("reset-token")) {
            web.ensureToken(true);
            plugin.getMessageManager().send(sender, "web-token-reset");
            plugin.getLogManager().log(sender, "WEB TOKEN RESET", null, null);
            if (!web.isRunning()) {
                return;
            }
        }
        if (!web.isRunning()) {
            plugin.getMessageManager().send(sender, "web-disabled");
            return;
        }
        String url = web.buildLink();
        MessageManager messages = plugin.getMessageManager();
        sender.sendMessage(messages.parseWithPlaceholders(messages.getPrefix() + messages.get("web-link"), Map.of("url", url))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url)));
    }

    // Lets /lagmap reuse everything here as if "/dt lagmap" was typed.
    public PrefixedCommand prefixed(String subcommand) {
        return new PrefixedCommand(subcommand);
    }

    public final class PrefixedCommand implements CommandExecutor, TabCompleter {

        private final String subcommand;

        private PrefixedCommand(String subcommand) {
            this.subcommand = subcommand;
        }

        private String[] withPrefix(String[] args) {
            String[] full = new String[args.length + 1];
            full[0] = subcommand;
            System.arraycopy(args, 0, full, 1, args.length);
            return full;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            return DimensionToggleCommand.this.onCommand(sender, command, label, withPrefix(args));
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            return DimensionToggleCommand.this.onTabComplete(sender, command, alias, withPrefix(args));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return COMMANDS_BY_WEIGHT.stream()
                    .filter(info -> info.permission() == null || sender.hasPermission(info.permission()))
                    .map(CommandInfo::name)
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("nether") || args[0].equalsIgnoreCase("end"))) {
            return STATES.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("maintenance") || args[0].equalsIgnoreCase("limit")
                || args[0].equalsIgnoreCase("softlock"))) {
            return List.of("nether", "end").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("lagmap") || args[0].equalsIgnoreCase("redstone")
                || args[0].equalsIgnoreCase("chunkloaders"))) {
            return List.of("chat").stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("lagmap") && args[1].equalsIgnoreCase("chat")) {
            return SORTS.stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("web")) {
            return List.of("reset-token").stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("maintenance")) {
            return MAINTENANCE_ACTIONS.stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
