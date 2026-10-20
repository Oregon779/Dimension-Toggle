package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.config.ConfigValueWriter;
import net.dimensiontoggle.manager.MaintenanceManager;
import net.dimensiontoggle.manager.MobManagementManager;
import net.dimensiontoggle.model.ToggleDimension;
import net.dimensiontoggle.model.WorldScope;
import net.dimensiontoggle.performance.LagMapScanner;
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

    private enum MenuType {
        MAIN, DIMENSION, PLAYERLIST, MAINTENANCE, MAINTENANCE_CUSTOM, SCHEDULE, MOBMANAGEMENT, WORLDBORDER,
        OVERWORLD, OVERWORLD_MOB_CATEGORIES, OVERWORLD_MOBS, REPORT_LAG, REPORT_REDSTONE, REPORT_LOADERS
    }

    // Which group/page of the Overworld mob list a player is looking at.
    private record MobView(MobManagementManager.Category category, int page) {
    }

    // Shift-click cleanup in the lag map needs a second shift-click on the
    // same chunk within this time.
    private static final long CLEANUP_CONFIRM_MILLIS = 5000;

    private record PendingCleanup(String chunkId, long until) {
    }

    // `inventory` is the exact top inventory this menu was opened with. Every
    // refresh/click checks it against what the player is actually looking at,
    // so a stale entry can never write into (or read actions from) someone
    // else's inventory.
    private record OpenMenu(MenuType type, ToggleDimension dimension, Inventory inventory) {
    }

    private final DimensionToggle plugin;
    private final Map<UUID, OpenMenu> openMenus = new HashMap<>();
    private final Map<UUID, LagMapScanner.Sort> lagSort = new HashMap<>();
    private final Map<UUID, PendingCleanup> pendingCleanup = new HashMap<>();
    private final Map<UUID, MobView> mobViews = new HashMap<>();
    private final ChatInput chatInput;
    private BukkitTask refreshTask;
    // Feedback sound of the click being handled (set by the toggles).
    private String clickSound;

    public GuiManager(DimensionToggle plugin) {
        this.plugin = plugin;
        this.chatInput = new ChatInput(plugin);
    }

    public ChatInput getChatInput() {
        return chatInput;
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
        chatInput.clearAll();
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

    public void openLagmap(Player player) {
        plugin.getLagMapScanner().invalidate();
        open(player, MenuType.REPORT_LAG, null);
    }

    public void openRedstone(Player player) {
        open(player, MenuType.REPORT_REDSTONE, null);
    }

    public void openChunkLoaders(Player player) {
        open(player, MenuType.REPORT_LOADERS, null);
    }

    private void open(Player player, MenuType type, ToggleDimension dimension) {
        if (type == MenuType.MAINTENANCE_CUSTOM) {
            openMaintenanceCustom(player, dimension);
            return;
        }
        Inventory inventory = build(type, dimension, player.getUniqueId());
        player.openInventory(inventory);
        // After openInventory: opening fires the close event for the previous
        // menu, which removes its entry.
        openMenus.put(player.getUniqueId(), new OpenMenu(type, dimension, inventory));
    }

    private Inventory build(MenuType type, ToggleDimension dimension, UUID viewer) {
        return switch (type) {
            case MAIN -> MainGuiBuilder.build(plugin);
            case DIMENSION -> DimensionGuiBuilder.build(plugin, dimension);
            case PLAYERLIST -> PlayerListGuiBuilder.build(plugin, dimension);
            case MAINTENANCE -> MaintenanceGuiBuilder.build(plugin, dimension);
            case SCHEDULE -> ScheduleGuiBuilder.build(plugin, dimension);
            case MOBMANAGEMENT -> MobManagementGuiBuilder.build(plugin, WorldScope.of(dimension), null, 0);
            case OVERWORLD -> OverworldGuiBuilder.build(plugin);
            case OVERWORLD_MOB_CATEGORIES -> MobCategoryGuiBuilder.build(plugin);
            case OVERWORLD_MOBS -> {
                MobView view = mobView(viewer);
                yield MobManagementGuiBuilder.build(plugin, WorldScope.OVERWORLD, view.category(), view.page());
            }
            case REPORT_LAG -> ChunkReportGuiBuilder.build(plugin, ChunkReportGuiBuilder.Mode.LAG,
                    lagSort.getOrDefault(viewer, LagMapScanner.Sort.SCORE), pendingChunk(viewer));
            case REPORT_REDSTONE -> ChunkReportGuiBuilder.build(plugin, ChunkReportGuiBuilder.Mode.REDSTONE,
                    LagMapScanner.Sort.SCORE, null);
            case REPORT_LOADERS -> ChunkReportGuiBuilder.build(plugin, ChunkReportGuiBuilder.Mode.LOADERS,
                    LagMapScanner.Sort.SCORE, null);
            case WORLDBORDER -> WorldBorderGuiBuilder.build(plugin, dimension);
            case MAINTENANCE_CUSTOM -> throw new IllegalArgumentException("Anvil menu is not built from a builder");
        };
    }

    // A real anvil menu: an inventory created via Bukkit.createInventory(ANVIL)
    // only looks like an anvil to the client - the server ignores rename input
    // for it, so PrepareAnvilEvent/getRenameText never see what was typed.
    // MenuType (Paper 26) also lets the anvil carry the configured title again.
    private void openMaintenanceCustom(Player player, ToggleDimension dimension) {
        String rawTitle = plugin.getGuiConfigManager().getFor(dimension)
                .getString("maintenance.custom-title", "<aqua><bold>Custom Time");
        AnvilView view = org.bukkit.inventory.MenuType.ANVIL.builder()
                .title(plugin.getMessageManager().parse(rawTitle))
                .checkReachable(false)
                .build(player);
        player.openInventory(view);
        if (player.getOpenInventory() != view) {
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
            MenuType.MAIN, MenuType.DIMENSION, MenuType.PLAYERLIST, MenuType.MAINTENANCE, MenuType.MOBMANAGEMENT,
            MenuType.OVERWORLD, MenuType.OVERWORLD_MOB_CATEGORIES, MenuType.OVERWORLD_MOBS);

    private MobView mobView(UUID viewer) {
        return mobViews.getOrDefault(viewer, new MobView(MobManagementManager.Category.MONSTERS, 0));
    }

    private void refreshInPlace(OpenMenu menu, UUID viewer) {
        if (menu.type() != MenuType.MAINTENANCE_CUSTOM) {
            menu.inventory().setContents(build(menu.type(), menu.dimension(), viewer).getContents());
        }
    }

    private String pendingChunk(UUID viewer) {
        PendingCleanup pending = pendingCleanup.get(viewer);
        if (pending == null || pending.until() < System.currentTimeMillis()) {
            pendingCleanup.remove(viewer);
            return null;
        }
        return pending.chunkId();
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
                refreshInPlace(menu, entry.getKey());
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
        afterClick(player, menu, () -> refreshInPlace(menu, player.getUniqueId()));
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
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        lagSort.remove(event.getPlayer().getUniqueId());
        pendingCleanup.remove(event.getPlayer().getUniqueId());
        mobViews.remove(event.getPlayer().getUniqueId());
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
            clickSound = "click";
            handleAction(player, menu, action, event.getClick());
            plugin.getSoundManager().playGui(player, clickSound);
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
            case "MAIN_OPEN_OVERWORLD" -> navigate(player, menu, MenuType.OVERWORLD, null);
            case "MAIN_LOCKDOWN" -> {
                plugin.getDimensionManager().toggleLockdownWithBroadcast(player);
                toggled(plugin.getDimensionManager().isLockdownActive());
                refresh(player, menu);
            }
            case "MAIN_CLOSE" -> afterClick(player, menu, player::closeInventory);

            case "DIM_TOGGLE" -> {
                plugin.getDimensionManager().setEnabled(dim, !plugin.getDimensionManager().isEnabled(dim), player);
                toggled(plugin.getDimensionManager().isEnabled(dim));
                refresh(player, menu);
            }
            case "DIM_SOFTLOCK" -> {
                plugin.getDimensionManager().toggleSoftLockWithBroadcast(dim, player);
                toggled(plugin.getDimensionManager().isLocked(dim));
                refresh(player, menu);
            }
            case "DIM_PLAYERLIST" -> navigate(player, menu, MenuType.PLAYERLIST, dim);
            case "DIM_KEEPINV" -> {
                toggled(plugin.getKeepInventoryManager().toggle(dim));
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
                toggled(plugin.getMobSpawnManager().toggle(dim));
                refresh(player, menu);
            }
            case "DIM_PVP" -> {
                toggled(plugin.getPvpIntegrationManager().toggle(dim));
                refresh(player, menu);
            }
            case "DIM_SPAWNERS" -> {
                toggled(plugin.getSpawnerToggleManager().toggle(dim));
                refresh(player, menu);
            }
            case "DIM_ELYTRA" -> {
                toggled(plugin.getElytraFlyManager().toggle());
                refresh(player, menu);
            }
            case "DIM_GATEWAY" -> {
                toggled(!toggleGateway());
                refresh(player, menu);
            }
            case "DIM_PVP_UNAVAILABLE" -> clickSound = "error";
            case "DIM_BACK" -> navigate(player, menu, MenuType.MAIN, null);

            case "MOB_BACK" -> navigate(player, menu,
                    menu.type() == MenuType.OVERWORLD_MOBS ? MenuType.OVERWORLD_MOB_CATEGORIES : MenuType.DIMENSION, dim);
            case "MOB_PAGE_NEXT", "MOB_PAGE_PREVIOUS" -> {
                MobView view = mobView(player.getUniqueId());
                int pages = MobManagementGuiBuilder.pageCount(view.category().mobs());
                int page = Math.clamp(view.page() + (action.equals("MOB_PAGE_NEXT") ? 1 : -1), 0, pages - 1);
                mobViews.put(player.getUniqueId(), new MobView(view.category(), page));
                // The page number is part of the title, so reopen instead of redrawing.
                navigate(player, menu, MenuType.OVERWORLD_MOBS, null);
            }
            case "MOBCAT_BACK" -> navigate(player, menu, MenuType.OVERWORLD, null);
            case "PLIST_BACK", "MAINT_BACK", "SCHED_BACK", "WB_BACK" ->
                    navigate(player, menu, MenuType.DIMENSION, dim);

            case "OW_BACK" -> navigate(player, menu, MenuType.MAIN, null);
            case "OW_MOBMANAGEMENT" -> navigate(player, menu, MenuType.OVERWORLD_MOB_CATEGORIES, null);
            case "OW_MOBSPAWN" -> {
                toggled(plugin.getMobSpawnManager().toggle(WorldScope.OVERWORLD));
                refresh(player, menu);
            }
            case "OW_FARMLIMITER" -> {
                boolean on = toggleConfigFlag(player, "farm-limiter", "enabled", false);
                toggled(on);
                if (on) {
                    plugin.getFarmLimiter().start();
                } else if (!plugin.getFarmLimiter().isActive()) {
                    plugin.getFarmLimiter().unfreezeAll();
                }
                refresh(player, menu);
            }
            case "OW_ENTITYCAP" -> {
                toggled(toggleConfigFlag(player, "entity-cap", "enabled", false));
                refresh(player, menu);
            }
            case "OW_VILLAGER" -> {
                boolean on = toggleConfigFlag(player, "villager-optimizer", "enabled", false);
                toggled(on);
                if (!on) {
                    plugin.getVillagerOptimizer().unfreezeAll();
                }
                refresh(player, menu);
            }
            case "OW_ITEMCLEANER" -> {
                if (click == ClickType.RIGHT) {
                    askItemCleanerInterval(player, menu);
                    return;
                }
                toggled(toggleConfigFlag(player, "item-cleaner", "enabled", false));
                plugin.getItemCleaner().start();
                refresh(player, menu);
            }
            case "OW_AFKVIEW" -> {
                boolean on = toggleConfigFlag(player, "afk-view", "enabled", false);
                toggled(on);
                if (!on && !plugin.getAfkViewManager().isActive()) {
                    plugin.getAfkViewManager().restoreAll();
                }
                refresh(player, menu);
            }
            case "OW_AUTOPERF" -> {
                toggled(toggleConfigFlag(player, "auto-performance", "enabled", false));
                refresh(player, menu);
            }
            case "OW_LAGMAP" -> {
                plugin.getLagMapScanner().invalidate();
                navigate(player, menu, MenuType.REPORT_LAG, null);
            }
            case "OW_REDSTONE" -> navigate(player, menu, MenuType.REPORT_REDSTONE, null);
            case "OW_CHUNKLOADERS" -> navigate(player, menu, MenuType.REPORT_LOADERS, null);
            case "OW_WEB" -> afterClick(player, menu, () -> {
                player.closeInventory();
                player.performCommand("dt web");
            });

            case "CR_BACK" -> navigate(player, menu, MenuType.OVERWORLD, null);
            case "CR_REFRESH" -> {
                plugin.getLagMapScanner().invalidate();
                refresh(player, menu);
            }
            case "CR_SORT" -> {
                lagSort.merge(player.getUniqueId(), LagMapScanner.Sort.SCORE.next(), (old, ignored) -> old.next());
                refresh(player, menu);
            }

            case "MAINT_CANCEL" -> {
                plugin.getMaintenanceManager().cancel(dim, player);
                refresh(player, menu);
            }
            case "MAINT_CUSTOM" -> navigate(player, menu, MenuType.MAINTENANCE_CUSTOM, dim);

            case "SCHED_TOGGLE_ENABLED" -> {
                toggled(toggleScheduleBoolean(dim, "enabled", false));
                refresh(player, menu);
            }
            case "SCHED_TOGGLE_COUNTDOWN" -> {
                toggled(toggleScheduleBoolean(dim, "countdown-enabled", true));
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
        if (action.startsWith("CR_TP|")) {
            handleReportEntry(player, menu, action.substring("CR_TP|".length()), click);
        } else if (action.startsWith("PLIST_TP_")) {
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
        } else if (action.startsWith("MOBCAT_")) {
            try {
                MobManagementManager.Category category = MobManagementManager.Category.valueOf(action.substring("MOBCAT_".length()));
                mobViews.put(player.getUniqueId(), new MobView(category, 0));
                navigate(player, menu, MenuType.OVERWORLD_MOBS, null);
            } catch (IllegalArgumentException ignored) {
                // unknown category - nothing to open
            }
        } else if (action.startsWith("MOB_TOGGLE_")) {
            handleMobToggle(player, menu, menu.type() == MenuType.OVERWORLD_MOBS ? WorldScope.OVERWORLD : WorldScope.of(dim),
                    action.substring("MOB_TOGGLE_".length()), click);
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

    // Flips a feature's "enabled" flag in memory and in config.yml (keeping
    // the file's comments). Returns the new value.
    private boolean toggleConfigFlag(Player player, String section, String key, boolean def) {
        String path = section + "." + key;
        boolean value = !plugin.getConfigManager().getConfig().getBoolean(path, def);
        plugin.getConfigManager().getConfig().set(path, value);
        plugin.getConfigManager().persistConfigEdit(path,
                file -> ConfigValueWriter.setNestedValue(file, String.valueOf(value), section, key));
        plugin.getLogManager().log(player, value ? "FEATURE ON" : "FEATURE OFF", null, section);
        return value;
    }

    // Report entries: click = teleport there. In the lag map, shift-click
    // cleans the chunk - but only after a second shift-click to confirm.
    private void handleReportEntry(Player player, OpenMenu menu, String chunkId, ClickType click) {
        String[] parts = chunkId.split("\\|");
        if (parts.length != 3) {
            return;
        }
        int x;
        int z;
        try {
            x = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }
        String world = parts[0];
        if (menu.type() == MenuType.REPORT_LAG && click.isShiftClick()) {
            if (chunkId.equals(pendingChunk(player.getUniqueId()))) {
                pendingCleanup.remove(player.getUniqueId());
                LagMapScanner.CleanupResult result = plugin.getLagMapScanner().cleanup(world, x, z);
                plugin.getMessageManager().send(player, "lagmap-cleaned", Map.of(
                        "items", String.valueOf(result.items()), "mobs", String.valueOf(result.mobs()),
                        "world", world, "x", String.valueOf(x), "z", String.valueOf(z)));
                plugin.getLogManager().log(player, "LAGMAP CLEANUP", null, world + " " + x + "," + z
                        + " (" + result.items() + " items, " + result.mobs() + " mobs)");
            } else {
                pendingCleanup.put(player.getUniqueId(),
                        new PendingCleanup(chunkId, System.currentTimeMillis() + CLEANUP_CONFIRM_MILLIS));
            }
            refresh(player, menu);
            return;
        }
        afterClick(player, menu, () -> {
            player.closeInventory();
            player.performCommand("dt tpchunk " + world + " " + x + " " + z);
        });
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

    private void toggled(boolean nowOn) {
        clickSound = nowOn ? "toggle-on" : "toggle-off";
    }

    // Returns whether gateways are blocked now.
    private boolean toggleGateway() {
        boolean current = plugin.getConfigManager().getConfig().getBoolean("block-end-gateways", true);
        plugin.getConfigManager().getConfig().set("block-end-gateways", !current);
        plugin.getConfigManager().persistConfigEdit("block-end-gateways",
                path -> ConfigValueWriter.setNestedValue(path, String.valueOf(!current), "block-end-gateways"));
        return !current;
    }

    private boolean toggleScheduleBoolean(ToggleDimension dimension, String key, boolean defaultValue) {
        String path = "schedule." + dimension.getKey() + "." + key;
        boolean current = plugin.getConfigManager().getConfig().getBoolean(path, defaultValue);
        plugin.getConfigManager().getConfig().set(path, !current);
        plugin.getConfigManager().persistConfigEdit(path,
                file -> ConfigValueWriter.setNestedValue(file, String.valueOf(!current), "schedule", dimension.getKey(), key));
        return !current;
    }

    // Closes the menu, asks for a time in the chat and opens the same menu
    // again afterwards (also after a cancel or the timeout).
    private void askInChat(Player player, OpenMenu menu, String what, String current, java.util.function.IntConsumer apply) {
        afterClick(player, menu, () -> {
            player.closeInventory();
            chatInput.ask(player, new ChatInput.Request(what, current, apply,
                    () -> open(player, menu.type(), menu.dimension())));
        });
    }

    private void askItemCleanerInterval(Player player, OpenMenu menu) {
        var config = plugin.getConfigManager().getConfig();
        String current = config.getBoolean("item-cleaner.enabled", false)
                ? plugin.getMessageManager().formatDuration(Math.max(1, config.getInt("item-cleaner.interval-minutes", 10)) * 60)
                : plugin.getMessageManager().get("chat-input-off-word", "off");
        askInChat(player, menu, plugin.getMessageManager().get("chat-input-what-item-cleaner", "Item cleaner"), current,
                minutes -> setItemCleanerInterval(player, minutes));
    }

    // 0 switches the cleaner off; any other value sets the interval and
    // switches it on.
    private void setItemCleanerInterval(Player player, int minutes) {
        var config = plugin.getConfigManager().getConfig();
        boolean enable = minutes > 0;
        if (enable) {
            config.set("item-cleaner.interval-minutes", minutes);
            plugin.getConfigManager().persistConfigEdit("item-cleaner.interval-minutes",
                    file -> ConfigValueWriter.setNestedValue(file, String.valueOf(minutes), "item-cleaner", "interval-minutes"));
        }
        if (config.getBoolean("item-cleaner.enabled", false) != enable) {
            toggleConfigFlag(player, "item-cleaner", "enabled", false);
        }
        plugin.getLogManager().log(player, "ITEM CLEANER INTERVAL", null, enable ? minutes + " min" : "off");
        plugin.getItemCleaner().start();
    }

    private void setScheduleTime(ToggleDimension dimension, String key, String time) {
        String path = "schedule." + dimension.getKey() + "." + key;
        plugin.getConfigManager().getConfig().set(path, time);
        plugin.getConfigManager().persistConfigEdit(path,
                file -> ConfigValueWriter.setNestedValue(file, "\"" + time + "\"", "schedule", dimension.getKey(), key));
    }

    // Click: spawning on/off · shift-click: cleanup presets · right-click:
    // own cleanup interval in the chat.
    private void handleMobToggle(Player player, OpenMenu menu, WorldScope dimension, String entityTypeName, ClickType click) {
        EntityType type;
        try {
            type = EntityType.valueOf(entityTypeName);
        } catch (IllegalArgumentException e) {
            return;
        }

        MobManagementManager mobs = plugin.getMobManagementManager();
        if (click.isShiftClick()) {
            toggled(mobs.cycleCleanupMinutes(dimension, type) > 0);
        } else if (click == ClickType.RIGHT) {
            int minutes = mobs.getCleanupMinutes(dimension, type);
            String current = minutes > 0 ? plugin.getMessageManager().formatDuration(minutes * 60)
                    : plugin.getMessageManager().get("chat-input-off-word", "off");
            String what = GuiItems.sub(plugin.getMessageManager().get("chat-input-what-mob", "%mob% (%dimension%)"), Map.of(
                    "mob", MobManagementGuiBuilder.prettifyName(type.name()),
                    "dimension", plugin.getMessageManager().getDimensionName(dimension.getKey())));
            askInChat(player, menu, what, current, value -> {
                mobs.setCleanupMinutes(dimension, type, value);
                plugin.getLogManager().log(player, "MOB CLEANUP INTERVAL", null,
                        dimension.getKey() + " " + type.name() + " " + (value > 0 ? value + " min" : "off"));
            });
            return;
        } else {
            toggled(mobs.toggleSpawnEnabled(dimension, type));
        }
        refresh(player, menu);
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

        long updated = clampBorderSize(world, current + delta);
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
        long clamped = clampBorderSize(world, size);
        try {
            world.getWorldBorder().setSize(clamped);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Could not set world border size to " + clamped + ": " + e.getMessage());
        }
    }

    // Paper rejects sizes above WorldBorder#getMaxSize() (59,999,968 - not
    // the round 60,000,000 the "maximum" preset shows), so clamp to what the
    // server actually accepts instead of a hard-coded number.
    private static long clampBorderSize(World world, long size) {
        long max = (long) world.getWorldBorder().getMaxSize();
        return Math.min(max, Math.max(1, size));
    }

    private void centerWorldBorder(ToggleDimension dimension) {
        World world = dimension.findWorld();
        if (world != null) {
            world.getWorldBorder().setCenter(0, 0);
        }
    }
}
