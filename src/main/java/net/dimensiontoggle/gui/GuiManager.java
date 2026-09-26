package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.config.ConfigValueWriter;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuiManager implements Listener {

    private enum MenuType { MAIN, DIMENSION, PLAYERLIST, MAINTENANCE, MAINTENANCE_CUSTOM, SCHEDULE, MOBMANAGEMENT, WORLDBORDER }

    private record OpenMenu(MenuType type, ToggleDimension dimension) {
    }

    private final DimensionToggle plugin;
    private final Map<UUID, OpenMenu> openMenus = new HashMap<>();
    private BukkitTask refreshTask;

    public GuiManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void start() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 20L);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void openMain(Player player) {
        player.openInventory(MainGuiBuilder.build(plugin));
        openMenus.put(player.getUniqueId(), new OpenMenu(MenuType.MAIN, null));
    }

    private void openDimension(Player player, ToggleDimension dimension) {
        player.openInventory(DimensionGuiBuilder.build(plugin, dimension));
        openMenus.put(player.getUniqueId(), new OpenMenu(MenuType.DIMENSION, dimension));
    }

    private void openPlayerList(Player player, ToggleDimension dimension) {
        player.openInventory(PlayerListGuiBuilder.build(plugin, dimension));
        openMenus.put(player.getUniqueId(), new OpenMenu(MenuType.PLAYERLIST, dimension));
    }

    private void openMaintenance(Player player, ToggleDimension dimension) {
        player.openInventory(MaintenanceGuiBuilder.build(plugin, dimension));
        openMenus.put(player.getUniqueId(), new OpenMenu(MenuType.MAINTENANCE, dimension));
    }

    private void openMaintenanceCustom(Player player, ToggleDimension dimension) {
        Inventory inv = Bukkit.createInventory(null, InventoryType.ANVIL,
                plugin.getMessageManager().parse("&6&lType a duration, e.g. 1h30m"));

        ItemStack hint = new ItemStack(Material.PAPER);
        ItemMeta meta = hint.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("1h30m"));
        hint.setItemMeta(meta);
        inv.setItem(0, hint);

        player.openInventory(inv);
        openMenus.put(player.getUniqueId(), new OpenMenu(MenuType.MAINTENANCE_CUSTOM, dimension));
    }

    private void openSchedule(Player player, ToggleDimension dimension) {
        player.openInventory(ScheduleGuiBuilder.build(plugin, dimension));
        openMenus.put(player.getUniqueId(), new OpenMenu(MenuType.SCHEDULE, dimension));
    }

    private void openMobManagement(Player player, ToggleDimension dimension) {
        player.openInventory(MobManagementGuiBuilder.build(plugin, dimension));
        openMenus.put(player.getUniqueId(), new OpenMenu(MenuType.MOBMANAGEMENT, dimension));
    }

    private void openWorldBorder(Player player, ToggleDimension dimension) {
        player.openInventory(WorldBorderGuiBuilder.build(plugin, dimension));
        openMenus.put(player.getUniqueId(), new OpenMenu(MenuType.WORLDBORDER, dimension));
    }

    private void refreshAll() {
        for (Map.Entry<UUID, OpenMenu> entry : new HashMap<>(openMenus).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                openMenus.remove(entry.getKey());
                continue;
            }

            OpenMenu menu = entry.getValue();
            Inventory top = player.getOpenInventory().getTopInventory();

            switch (menu.type()) {
                case MAIN -> top.setContents(MainGuiBuilder.build(plugin).getContents());
                case DIMENSION -> top.setContents(DimensionGuiBuilder.build(plugin, menu.dimension()).getContents());
                case PLAYERLIST -> top.setContents(PlayerListGuiBuilder.build(plugin, menu.dimension()).getContents());
                case MAINTENANCE -> top.setContents(MaintenanceGuiBuilder.build(plugin, menu.dimension()).getContents());
                case MOBMANAGEMENT -> top.setContents(MobManagementGuiBuilder.build(plugin, menu.dimension()).getContents());
                default -> {

                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        OpenMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }

        if (menu.type() == MenuType.MAINTENANCE_CUSTOM) {
            handleMaintenanceCustomClick(player, menu, event);
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        String action = GuiItems.readAction(plugin, clicked);
        if (action == null) {
            return;
        }

        handleAction(player, menu, action, event.getClick());
    }

    private void handleMaintenanceCustomClick(Player player, OpenMenu menu, InventoryClickEvent event) {
        event.setCancelled(true);

        if (event.getRawSlot() != 2) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || !result.hasItemMeta() || !result.getItemMeta().hasDisplayName()) {
            return;
        }

        net.kyori.adventure.text.Component displayName = result.getItemMeta().displayName();
        String typed = displayName == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName);
        Integer seconds = net.dimensiontoggle.manager.MaintenanceManager.parseDurationToSeconds(typed);
        if (seconds == null || seconds <= 0) {
            return;
        }

        plugin.getMaintenanceManager().start(menu.dimension(), seconds, player);
        openDimension(player, menu.dimension());
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        OpenMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null || menu.type() != MenuType.MAINTENANCE_CUSTOM) {
            return;
        }

        String typed = event.getView().getRenameText();
        if (typed == null || typed.isBlank()) {
            typed = "1h30m";
        }

        ItemStack result = new ItemStack(Material.PAPER);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(typed));
        result.setItemMeta(meta);
        event.setResult(result);
    }

    private void handleAction(Player player, OpenMenu menu, String action, ClickType click) {
        switch (action) {
            case "MAIN_OPEN_NETHER" -> openDimension(player, ToggleDimension.NETHER);
            case "MAIN_OPEN_END" -> openDimension(player, ToggleDimension.END);
            case "MAIN_LOCKDOWN" -> {
                plugin.getDimensionManager().toggleLockdownWithBroadcast(player);
                openMain(player);
            }
            case "MAIN_CLOSE" -> player.closeInventory();

            case "DIM_TOGGLE" -> {
                plugin.getDimensionManager().setEnabled(menu.dimension(), !plugin.getDimensionManager().isEnabled(menu.dimension()), player);
                openDimension(player, menu.dimension());
            }
            case "DIM_SOFTLOCK" -> {
                plugin.getDimensionManager().toggleSoftLockWithBroadcast(menu.dimension(), player);
                openDimension(player, menu.dimension());
            }
            case "DIM_PLAYERLIST" -> openPlayerList(player, menu.dimension());
            case "DIM_KEEPINV" -> {
                plugin.getKeepInventoryManager().toggle(menu.dimension());
                openDimension(player, menu.dimension());
            }
            case "DIM_LIMIT" -> {
                adjustLimit(menu.dimension(), click);
                openDimension(player, menu.dimension());
            }
            case "DIM_MAINTENANCE" -> openMaintenance(player, menu.dimension());
            case "DIM_SCHEDULE" -> openSchedule(player, menu.dimension());
            case "DIM_MOBMANAGEMENT" -> openMobManagement(player, menu.dimension());
            case "DIM_WORLDBORDER" -> openWorldBorder(player, menu.dimension());
            case "DIM_MOBSPAWN" -> {
                plugin.getMobSpawnManager().toggle(menu.dimension());
                openDimension(player, menu.dimension());
            }
            case "DIM_PVP" -> {
                plugin.getPvpIntegrationManager().toggle(menu.dimension());
                openDimension(player, menu.dimension());
            }
            case "DIM_SPAWNERS" -> {
                plugin.getSpawnerToggleManager().toggle(menu.dimension());
                openDimension(player, menu.dimension());
            }
            case "DIM_ELYTRA" -> {
                plugin.getElytraFlyManager().toggle();
                openDimension(player, menu.dimension());
            }
            case "DIM_GATEWAY" -> {
                toggleGateway();
                openDimension(player, menu.dimension());
            }
            case "DIM_BACK" -> openMain(player);

            case "PLIST_BACK" -> openDimension(player, menu.dimension());

            case "MAINT_CANCEL" -> {
                plugin.getMaintenanceManager().cancel(menu.dimension(), player);
                openMaintenance(player, menu.dimension());
            }
            case "MAINT_BACK" -> openDimension(player, menu.dimension());
            case "MAINT_CUSTOM" -> openMaintenanceCustom(player, menu.dimension());

            case "SCHED_TOGGLE_ENABLED" -> {
                toggleScheduleBoolean(menu.dimension(), "enabled");
                openSchedule(player, menu.dimension());
            }
            case "SCHED_TOGGLE_COUNTDOWN" -> {
                toggleScheduleBoolean(menu.dimension(), "countdown-enabled");
                openSchedule(player, menu.dimension());
            }
            case "SCHED_BACK" -> openDimension(player, menu.dimension());

            case "MOB_BACK" -> openDimension(player, menu.dimension());

            case "WB_CENTER" -> {
                centerWorldBorder(menu.dimension());
                openWorldBorder(player, menu.dimension());
            }
            case "WB_BACK" -> openDimension(player, menu.dimension());

            default -> handlePrefixedAction(player, menu, action, click);
        }
    }

    private void handlePrefixedAction(Player player, OpenMenu menu, String action, ClickType click) {
        if (action.startsWith("PLIST_TP_")) {
            teleportToPlayer(player, action.substring("PLIST_TP_".length()));
        } else if (action.startsWith("MAINT_START_")) {
            startMaintenance(player, menu.dimension(), action.substring("MAINT_START_".length()));
            openDimension(player, menu.dimension());
        } else if (action.startsWith("SCHED_OPEN_")) {
            setScheduleTime(menu.dimension(), "open-time", action.substring("SCHED_OPEN_".length()));
            openSchedule(player, menu.dimension());
        } else if (action.startsWith("SCHED_CLOSE_")) {
            setScheduleTime(menu.dimension(), "close-time", action.substring("SCHED_CLOSE_".length()));
            openSchedule(player, menu.dimension());
        } else if (action.startsWith("MOB_TOGGLE_")) {
            handleMobToggle(menu.dimension(), action.substring("MOB_TOGGLE_".length()), click);
            openMobManagement(player, menu.dimension());
        } else if (action.equals("WB_ADJUST")) {
            adjustWorldBorder(menu.dimension(), click);
            openWorldBorder(player, menu.dimension());
        } else if (action.startsWith("WB_PRESET_")) {
            setWorldBorder(menu.dimension(), Long.parseLong(action.substring("WB_PRESET_".length())));
            openWorldBorder(player, menu.dimension());
        }
    }

    private void adjustLimit(ToggleDimension dimension, ClickType click) {
        var section = plugin.getConfigManager().getConfig().getConfigurationSection("limits." + dimension.getKey());
        int current = section == null ? 0 : section.getInt("max-players", 0);

        int delta = switch (click) {
            case LEFT -> -1;
            case SHIFT_LEFT -> -10;
            case RIGHT -> 1;
            case SHIFT_RIGHT -> 10;
            default -> 0;
        };

        int updated = Math.max(0, current + delta);
        if (updated == current) {
            return;
        }

        plugin.getConfigManager().getConfig().set("limits." + dimension.getKey() + ".max-players", updated);
        plugin.getConfigManager().getConfig().set("limits." + dimension.getKey() + ".enabled", true);

        plugin.getConfigManager().persistConfigEdit("limits." + dimension.getKey(),
                path -> ConfigValueWriter.setLimit(path, dimension.getKey(), updated));
    }

    private void teleportToPlayer(Player admin, String uuidString) {
        try {
            UUID targetId = UUID.fromString(uuidString);
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                admin.closeInventory();
                admin.teleportAsync(target.getLocation());
            }
        } catch (IllegalArgumentException ignored) {

        }
    }

    private void startMaintenance(Player player, ToggleDimension dimension, String preset) {
        Integer seconds = net.dimensiontoggle.manager.MaintenanceManager.parseDurationToSeconds(preset);
        if (seconds == null || seconds <= 0) {
            return;
        }
        plugin.getMaintenanceManager().start(dimension, seconds, player);
    }

    private void toggleGateway() {
        boolean current = plugin.getConfigManager().getConfig().getBoolean("block-end-gateways", true);
        plugin.getConfigManager().getConfig().set("block-end-gateways", !current);
        plugin.getConfigManager().persistConfigEdit("block-end-gateways",
                path -> ConfigValueWriter.setNestedValue(path, String.valueOf(!current), "block-end-gateways"));
    }

    private void toggleScheduleBoolean(ToggleDimension dimension, String key) {
        String path = "schedule." + dimension.getKey() + "." + key;
        boolean current = plugin.getConfigManager().getConfig().getBoolean(path, false);
        plugin.getConfigManager().getConfig().set(path, !current);
        plugin.getConfigManager().persistConfigEdit(path,
                file -> ConfigValueWriter.setNestedValue(file, String.valueOf(!current), "schedule", dimension.getKey(), key));
    }

    private void setScheduleTime(ToggleDimension dimension, String key, String time) {
        String path = "schedule." + dimension.getKey() + "." + key;
        plugin.getConfigManager().getConfig().set(path, time);
        plugin.getConfigManager().persistConfigEdit(path,
                file -> ConfigValueWriter.setNestedValue(file, "\"" + time + "\"", "schedule", dimension.getKey(), key));
    }

    private void handleMobToggle(ToggleDimension dimension, String entityTypeName, ClickType click) {
        EntityType type;
        try {
            type = EntityType.valueOf(entityTypeName);
        } catch (IllegalArgumentException e) {
            return;
        }

        if (click.isShiftClick()) {
            plugin.getMobManagementManager().cycleCleanupMinutes(dimension, type);
        } else {
            plugin.getMobManagementManager().toggleSpawnEnabled(dimension, type);
        }
    }

    private void adjustWorldBorder(ToggleDimension dimension, ClickType click) {
        World world = dimension.findWorld();
        if (world == null) {
            return;
        }

        long current = (long) world.getWorldBorder().getSize();
        long delta = switch (click) {
            case LEFT -> -100;
            case SHIFT_LEFT -> -1000;
            case RIGHT -> 100;
            case SHIFT_RIGHT -> 1000;
            default -> 0;
        };

        long updated = Math.min(60_000_000, Math.max(1, current + delta));
        if (updated == current) {
            return;
        }

        try {
            world.getWorldBorder().setSize(updated);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Could not set world border size to " + updated + ": " + e.getMessage());
        }

    }

    private void setWorldBorder(ToggleDimension dimension, long size) {
        World world = dimension.findWorld();
        if (world == null) {
            return;
        }
        long clamped = Math.min(60_000_000, Math.max(1, size));
        try {
            world.getWorldBorder().setSize(clamped);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Could not set world border size to " + clamped + ": " + e.getMessage());
        }
    }

    private void centerWorldBorder(ToggleDimension dimension) {
        World world = dimension.findWorld();
        if (world != null) {
            world.getWorldBorder().setCenter(0, 0);
        }
    }
}
