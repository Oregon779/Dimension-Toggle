package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.config.ConfigValueWriter;
import net.dimensiontoggle.manager.MaintenanceManager;
import net.dimensiontoggle.model.ToggleDimension;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuiManager implements Listener {

    private enum MenuType { MAIN, DIMENSION, PLAYERLIST, MAINTENANCE, MAINTENANCE_CUSTOM, SCHEDULE, MOBMANAGEMENT, WORLDBORDER }

    // `inventory` is the exact top inventory this menu was opened with. Every
    // refresh/click checks it against what the player is actually looking at,
    // so a stale entry can never write into (or read actions from) someone
    // else's inventory.
    private record OpenMenu(MenuType type, ToggleDimension dimension, Inventory inventory) {
    }

    private final DimensionToggle plugin;
    private final Map<UUID, OpenMenu> openMenus = new HashMap<>();
    private BukkitTask refreshTask;

    public GuiManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void start() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 23L, 20L);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    // For onDisable: once the plugin is disabled nothing cancels clicks in
    // these inventories anymore, so an admin could take the GUI items
    // (spawners, elytras, totems, ...) straight out of a menu left open.
    public void closeAllMenus() {
        for (Map.Entry<UUID, OpenMenu> entry : new HashMap<>(openMenus).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            // Our close listener no longer runs while disabling, so clear the
            // anvil here or it hands its input item to the player on close.
            if (entry.getValue().type() == MenuType.MAINTENANCE_CUSTOM) {
                entry.getValue().inventory().clear();
            }
            if (isShowing(player, entry.getValue())) {
                player.closeInventory();
            }
        }
        openMenus.clear();
    }

    public void openMain(Player player) {
        open(player, MenuType.MAIN, null);
    }

    private void open(Player player, MenuType type, ToggleDimension dimension) {
        if (type == MenuType.MAINTENANCE_CUSTOM) {
            openMaintenanceCustom(player, dimension);
            return;
        }
        Inventory inventory = build(type, dimension);
        player.openInventory(inventory);
        // After openInventory: opening fires the close event for the previous
        // menu, which removes its entry.
        openMenus.put(player.getUniqueId(), new OpenMenu(type, dimension, inventory));
    }

    private Inventory build(MenuType type, ToggleDimension dimension) {
        return switch (type) {
            case MAIN -> MainGuiBuilder.build(plugin);
            case DIMENSION -> DimensionGuiBuilder.build(plugin, dimension);
            case PLAYERLIST -> PlayerListGuiBuilder.build(plugin, dimension);
            case MAINTENANCE -> MaintenanceGuiBuilder.build(plugin, dimension);
            case SCHEDULE -> ScheduleGuiBuilder.build(plugin, dimension);
            case MOBMANAGEMENT -> MobManagementGuiBuilder.build(plugin, dimension);
            case WORLDBORDER -> WorldBorderGuiBuilder.build(plugin, dimension);
            case MAINTENANCE_CUSTOM -> throw new IllegalArgumentException("Anvil menu is not built from a builder");
        };
    }

    // A real anvil menu: an inventory created via Bukkit.createInventory(ANVIL)
    // only looks like an anvil to the client - the server ignores rename input
    // for it, so PrepareAnvilEvent/getRenameText never see what was typed.
    private void openMaintenanceCustom(Player player, ToggleDimension dimension) {
        InventoryView view = player.openAnvil(null, true);
        if (view == null) {
            return;
        }
        ItemStack hint = new ItemStack(Material.PAPER);
        ItemMeta meta = hint.getItemMeta();
        meta.displayName(Component.text("1h30m"));
        hint.setItemMeta(meta);
        Inventory top = view.getTopInventory();
        top.setItem(0, hint);
        openMenus.put(player.getUniqueId(), new OpenMenu(MenuType.MAINTENANCE_CUSTOM, dimension, top));
    }

    private static boolean isShowing(Player player, OpenMenu menu) {
        return player.getOpenInventory().getTopInventory() == menu.inventory();
    }

    // Menus showing live data (countdowns, player counts, dashboard) - the
    // others only change when clicked and are refreshed right after that.
    private static final java.util.Set<MenuType> LIVE_MENUS = java.util.EnumSet.of(
            MenuType.MAIN, MenuType.DIMENSION, MenuType.PLAYERLIST, MenuType.MAINTENANCE, MenuType.MOBMANAGEMENT);

    private void refreshInPlace(OpenMenu menu) {
        if (menu.type() != MenuType.MAINTENANCE_CUSTOM) {
            menu.inventory().setContents(build(menu.type(), menu.dimension()).getContents());
        }
    }

    private void refreshAll() {
        for (Map.Entry<UUID, OpenMenu> entry : new HashMap<>(openMenus).entrySet()) {
            OpenMenu menu = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !isShowing(player, menu)) {
                openMenus.remove(entry.getKey());
                continue;
            }
            if (LIVE_MENUS.contains(menu.type())) {
                refreshInPlace(menu);
            }
        }
    }

    // Bukkit forbids opening/closing inventories from inside an
    // InventoryClickEvent. The state change a click causes is applied right
    // away; showing the result is deferred to the next tick, and skipped if
    // the player has left that menu in the meantime.
    private void afterClick(Player player, OpenMenu clickedIn, Runnable uiUpdate) {
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && openMenus.get(id) == clickedIn && isShowing(player, clickedIn)) {
                uiUpdate.run();
            }
        });
    }

    private void navigate(Player player, OpenMenu from, MenuType to, ToggleDimension dimension) {
        afterClick(player, from, () -> open(player, to, dimension));
    }

    private void refresh(Player player, OpenMenu menu) {
        afterClick(player, menu, () -> refreshInPlace(menu));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        OpenMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null || event.getInventory() != menu.inventory()) {
            return;
        }
        if (menu.type() == MenuType.MAINTENANCE_CUSTOM) {
            // A real anvil returns its input items to the player on close.
            event.getInventory().clear();
        }
        openMenus.remove(player.getUniqueId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            OpenMenu menu = openMenus.get(player.getUniqueId());
            if (menu != null && event.getView().getTopInventory() == menu.inventory()) {
                event.setCancelled(true);
            }
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
        if (event.getView().getTopInventory() != menu.inventory()) {
            openMenus.remove(player.getUniqueId());
            return;
        }

        // Cancel every click while a menu is open, including in the player's
        // own inventory - otherwise shift-clicks could push items into it.
        event.setCancelled(true);

        // Only items in our own menu carry actions; an item in the player's
        // inventory could be a leftover copy of a GUI item.
        if (event.getClickedInventory() != menu.inventory()) {
            return;
        }

        if (menu.type() == MenuType.MAINTENANCE_CUSTOM) {
            handleMaintenanceCustomClick(player, menu, event);
            return;
        }

        String action = GuiItems.readAction(plugin, menu.inventory().getItem(event.getSlot()));
        if (action != null) {
            handleAction(player, menu, action, event.getClick());
        }
    }

    private void handleMaintenanceCustomClick(Player player, OpenMenu menu, InventoryClickEvent event) {
        if (event.getRawSlot() != 2) {
            return;
        }

        String typed = null;
        if (event.getView() instanceof AnvilView anvil) {
            typed = anvil.getRenameText();
        }
        if (typed == null || typed.isBlank()) {
            ItemStack result = menu.inventory().getItem(2);
            Component name = result == null || !result.hasItemMeta() ? null : result.getItemMeta().displayName();
            typed = name == null ? null : PlainTextComponentSerializer.plainText().serialize(name);
        }

        Integer seconds = MaintenanceManager.parseDurationToSeconds(typed);
        if (seconds == null) {
            return;
        }

        plugin.getMaintenanceManager().start(menu.dimension(), seconds, player);
        navigate(player, menu, MenuType.DIMENSION, menu.dimension());
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        OpenMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null || menu.type() != MenuType.MAINTENANCE_CUSTOM || event.getInventory() != menu.inventory()) {
            return;
        }

        String typed = event.getView().getRenameText();
        if (typed == null || typed.isBlank()) {
            typed = "1h30m";
        }

        ItemStack result = new ItemStack(Material.PAPER);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text(typed));
        result.setItemMeta(meta);
        event.setResult(result);
    }

    private void handleAction(Player player, OpenMenu menu, String action, ClickType click) {
        ToggleDimension dim = menu.dimension();
        switch (action) {
            case "MAIN_OPEN_NETHER" -> navigate(player, menu, MenuType.DIMENSION, ToggleDimension.NETHER);
            case "MAIN_OPEN_END" -> navigate(player, menu, MenuType.DIMENSION, ToggleDimension.END);
            case "MAIN_LOCKDOWN" -> {
                plugin.getDimensionManager().toggleLockdownWithBroadcast(player);
                refresh(player, menu);
            }
            case "MAIN_CLOSE" -> afterClick(player, menu, player::closeInventory);

            case "DIM_TOGGLE" -> {
                plugin.getDimensionManager().setEnabled(dim, !plugin.getDimensionManager().isEnabled(dim), player);
                refresh(player, menu);
            }
            case "DIM_SOFTLOCK" -> {
                plugin.getDimensionManager().toggleSoftLockWithBroadcast(dim, player);
                refresh(player, menu);
            }
            case "DIM_PLAYERLIST" -> navigate(player, menu, MenuType.PLAYERLIST, dim);
            case "DIM_KEEPINV" -> {
                plugin.getKeepInventoryManager().toggle(dim);
                refresh(player, menu);
            }
            case "DIM_LIMIT" -> {
                adjustLimit(dim, click);
                refresh(player, menu);
            }
            case "DIM_MAINTENANCE" -> navigate(player, menu, MenuType.MAINTENANCE, dim);
            case "DIM_SCHEDULE" -> navigate(player, menu, MenuType.SCHEDULE, dim);
            case "DIM_MOBMANAGEMENT" -> navigate(player, menu, MenuType.MOBMANAGEMENT, dim);
            case "DIM_WORLDBORDER" -> navigate(player, menu, MenuType.WORLDBORDER, dim);
            case "DIM_MOBSPAWN" -> {
                plugin.getMobSpawnManager().toggle(dim);
                refresh(player, menu);
            }
            case "DIM_PVP" -> {
                plugin.getPvpIntegrationManager().toggle(dim);
                refresh(player, menu);
            }
            case "DIM_SPAWNERS" -> {
                plugin.getSpawnerToggleManager().toggle(dim);
                refresh(player, menu);
            }
            case "DIM_ELYTRA" -> {
                plugin.getElytraFlyManager().toggle();
                refresh(player, menu);
            }
            case "DIM_GATEWAY" -> {
                toggleGateway();
                refresh(player, menu);
            }
            case "DIM_BACK" -> navigate(player, menu, MenuType.MAIN, null);

            case "PLIST_BACK", "MAINT_BACK", "SCHED_BACK", "MOB_BACK", "WB_BACK" ->
                    navigate(player, menu, MenuType.DIMENSION, dim);

            case "MAINT_CANCEL" -> {
                plugin.getMaintenanceManager().cancel(dim, player);
                refresh(player, menu);
            }
            case "MAINT_CUSTOM" -> navigate(player, menu, MenuType.MAINTENANCE_CUSTOM, dim);

            case "SCHED_TOGGLE_ENABLED" -> {
                toggleScheduleBoolean(dim, "enabled", false);
                refresh(player, menu);
            }
            case "SCHED_TOGGLE_COUNTDOWN" -> {
                toggleScheduleBoolean(dim, "countdown-enabled", true);
                refresh(player, menu);
            }

            case "WB_CENTER" -> {
                centerWorldBorder(dim);
                refresh(player, menu);
            }

            default -> handlePrefixedAction(player, menu, action, click);
        }
    }

    private void handlePrefixedAction(Player player, OpenMenu menu, String action, ClickType click) {
        ToggleDimension dim = menu.dimension();
        if (action.startsWith("PLIST_TP_")) {
            teleportToPlayer(player, menu, action.substring("PLIST_TP_".length()));
        } else if (action.startsWith("MAINT_START_")) {
            startMaintenance(player, dim, action.substring("MAINT_START_".length()));
            navigate(player, menu, MenuType.DIMENSION, dim);
        } else if (action.startsWith("SCHED_OPEN_")) {
            setScheduleTime(dim, "open-time", action.substring("SCHED_OPEN_".length()));
            refresh(player, menu);
        } else if (action.startsWith("SCHED_CLOSE_")) {
            setScheduleTime(dim, "close-time", action.substring("SCHED_CLOSE_".length()));
            refresh(player, menu);
        } else if (action.startsWith("MOB_TOGGLE_")) {
            handleMobToggle(dim, action.substring("MOB_TOGGLE_".length()), click);
            refresh(player, menu);
        } else if (action.equals("WB_ADJUST")) {
            adjustWorldBorder(dim, click);
            refresh(player, menu);
        } else if (action.startsWith("WB_PRESET_")) {
            try {
                setWorldBorder(dim, Long.parseLong(action.substring("WB_PRESET_".length())));
            } catch (NumberFormatException ignored) {
                return;
            }
            refresh(player, menu);
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

    private void teleportToPlayer(Player admin, OpenMenu menu, String uuidString) {
        UUID targetId;
        try {
            targetId = UUID.fromString(uuidString);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        afterClick(admin, menu, () -> {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                admin.closeInventory();
                admin.teleportAsync(target.getLocation());
            }
        });
    }

    private void startMaintenance(Player player, ToggleDimension dimension, String preset) {
        Integer seconds = MaintenanceManager.parseDurationToSeconds(preset);
        if (seconds == null) {
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

    private void toggleScheduleBoolean(ToggleDimension dimension, String key, boolean defaultValue) {
        String path = "schedule." + dimension.getKey() + "." + key;
        boolean current = plugin.getConfigManager().getConfig().getBoolean(path, defaultValue);
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
