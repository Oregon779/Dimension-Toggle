package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Six rows, mirrored around the middle column:
//   row 1  overview · ON/OFF switch · players inside
//   row 2  access + automation (soft-lock, limit | maintenance, schedule)
//   row 3  world rules (Nether 4, End 5)
//   row 4  management (mob management | world border)
//   row 5  back
// Every feature always shows its own icon; on = glow + green state.
public final class DimensionGuiBuilder {

    static final int ROWS = 6;
    static final int DASHBOARD_SLOT = 12;
    static final int TOGGLE_SLOT = 13;
    static final int PLAYERS_SLOT = 14;
    static final int BACK_SLOT = 49;

    private DimensionGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin, ToggleDimension dimension) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getFor(dimension);
        String base = "control-panel.";

        Inventory inventory = Bukkit.createInventory(null, ROWS * 9,
                plugin.getMessageManager().parse(cfg.getString(base + "title", "&5&lControl Panel")));

        boolean enabled = plugin.getDimensionManager().isEnabled(dimension);
        boolean locked = plugin.getDimensionManager().isLocked(dimension);
        boolean keepInv = plugin.getKeepInventoryManager().isEnabled(dimension);
        int playerCount = plugin.getDimensionManager().countPlayersInDimension(dimension);

        var limitSection = plugin.getConfigManager().getConfig().getConfigurationSection("limits." + dimension.getKey());
        boolean limitEnabled = limitSection != null && limitSection.getBoolean("enabled", false);
        int limit = limitSection == null ? 0 : limitSection.getInt("max-players", 0);

        // ---- Row 1: overview, the switch, players inside
        inventory.setItem(DASHBOARD_SLOT, buildDashboardItem(plugin, dimension, cfg, base));

        Map<String, String> actionPlaceholder = Map.of("action", cfg.getString(enabled ? "action-disable" : "action-enable", ""));
        inventory.setItem(TOGGLE_SLOT, GuiItems.build(plugin, enabled ? Material.LIME_DYE : Material.GRAY_DYE, "DIM_TOGGLE",
                cfg.getString(base + "toggle-button." + (enabled ? "name-enabled" : "name-disabled")),
                lore(cfg, base + "toggle-button.lore", actionPlaceholder), enabled));

        Map<String, String> countPlaceholder = Map.of("count", String.valueOf(playerCount));
        inventory.setItem(PLAYERS_SLOT, GuiItems.build(plugin, Material.PLAYER_HEAD, "DIM_PLAYERLIST",
                GuiItems.sub(cfg.getString(base + "playerlist-button.name"), countPlaceholder),
                GuiItems.subList(cfg.getStringList(base + "playerlist-button.lore"), countPlaceholder)));

        // ---- Row 2: access | automation
        List<ItemStack> access = new ArrayList<>();
        Map<String, String> lockActionPlaceholder = Map.of("action", cfg.getString(locked ? "action-unlock" : "action-lock", ""));
        access.add(GuiItems.build(plugin, Material.IRON_BARS, "DIM_SOFTLOCK",
                cfg.getString(base + "softlock-button." + (locked ? "name-locked" : "name-unlocked")),
                GuiItems.subList(cfg.getStringList(base + "softlock-button.lore"), lockActionPlaceholder), locked));

        Map<String, String> limitPlaceholders = new HashMap<>();
        limitPlaceholders.put("limit-status", cfg.getString(limitEnabled ? "limit-status-enabled" : "limit-status-disabled", ""));
        limitPlaceholders.put("limit", String.valueOf(limit));
        access.add(GuiItems.build(plugin, Material.TARGET, "DIM_LIMIT",
                cfg.getString(base + "limit-button.name"),
                GuiItems.subList(cfg.getStringList(base + "limit-button.lore"), limitPlaceholders), limitEnabled));

        boolean maintenance = plugin.getMaintenanceManager().isPending(dimension);
        access.add(GuiItems.build(plugin, Material.CLOCK, "DIM_MAINTENANCE",
                cfg.getString(base + "maintenance-button.name"), cfg.getStringList(base + "maintenance-button.lore"),
                maintenance));

        boolean scheduled = plugin.getConfigManager().getConfig().getBoolean("schedule." + dimension.getKey() + ".enabled", false);
        access.add(GuiItems.build(plugin, Material.RECOVERY_COMPASS, "DIM_SCHEDULE",
                cfg.getString(base + "schedule-button.name"), cfg.getStringList(base + "schedule-button.lore"), scheduled));
        GuiItems.placeOnAxis(inventory, 2, access);

        // ---- Row 3: world rules (same for both, plus the End/Nether extras)
        List<ItemStack> rules = new ArrayList<>();
        rules.add(GuiItems.build(plugin, Material.TOTEM_OF_UNDYING, "DIM_KEEPINV",
                cfg.getString(base + "keepinventory-button." + (keepInv ? "name-on" : "name-off")),
                cfg.getStringList(base + "keepinventory-button.lore"), keepInv));

        boolean pvpAvailable = plugin.getPvpIntegrationManager().isAvailable();
        boolean pvpEnabled = pvpAvailable && plugin.getPvpIntegrationManager().isPvpEnabled(dimension);
        String pvpKey = !pvpAvailable ? "name-unavailable" : pvpEnabled ? "name-on" : "name-off";
        rules.add(GuiItems.build(plugin, Material.IRON_SWORD, pvpAvailable ? "DIM_PVP" : "DIM_PVP_UNAVAILABLE",
                cfg.getString(base + "pvp-button." + pvpKey), cfg.getStringList(base + "pvp-button.lore-" + (pvpAvailable ? "available" : "unavailable")),
                pvpEnabled));

        boolean mobSpawningEnabled = plugin.getMobSpawnManager().isEnabled(dimension);
        rules.add(GuiItems.build(plugin, Material.ZOMBIE_SPAWN_EGG, "DIM_MOBSPAWN",
                cfg.getString(base + "mobspawn-button." + (mobSpawningEnabled ? "name-on" : "name-off")),
                cfg.getStringList(base + "mobspawn-button.lore"), mobSpawningEnabled));

        if (dimension == ToggleDimension.END) {
            boolean elytra = plugin.getElytraFlyManager().isEnabled();
            boolean gatewaysBlocked = plugin.getConfigManager().getConfig().getBoolean("block-end-gateways", true);

            rules.add(GuiItems.build(plugin, Material.ELYTRA, "DIM_ELYTRA",
                    cfg.getString(base + "elytra-button." + (elytra ? "name-on" : "name-off")),
                    cfg.getStringList(base + "elytra-button.lore"), elytra));

            rules.add(GuiItems.build(plugin, Material.ENDER_EYE, "DIM_GATEWAY",
                    cfg.getString(base + "gateway-button." + (gatewaysBlocked ? "name-blocked" : "name-allowed")),
                    cfg.getStringList(base + "gateway-button.lore"), !gatewaysBlocked));
        } else {
            boolean spawnersEnabled = plugin.getSpawnerToggleManager().isEnabled(dimension);

            rules.add(GuiItems.build(plugin, Material.SPAWNER, "DIM_SPAWNERS",
                    cfg.getString(base + "spawners-button." + (spawnersEnabled ? "name-on" : "name-off")),
                    cfg.getStringList(base + "spawners-button.lore"), spawnersEnabled));
        }
        GuiItems.placeOnAxis(inventory, 3, rules);

        // ---- Row 4: management
        GuiItems.placeOnAxis(inventory, 4, List.of(
                GuiItems.build(plugin, Material.ZOMBIE_HEAD, "DIM_MOBMANAGEMENT",
                        cfg.getString(base + "mobmanagement-button.name"), cfg.getStringList(base + "mobmanagement-button.lore")),
                GuiItems.build(plugin, Material.WHITE_STAINED_GLASS, "DIM_WORLDBORDER",
                        cfg.getString(base + "worldborder-button.name"), cfg.getStringList(base + "worldborder-button.lore"))));

        inventory.setItem(BACK_SLOT, GuiItems.build(plugin, Material.ARROW, "DIM_BACK",
                cfg.getString(base + "back-button.name"), lore(cfg, base + "back-button.lore", Map.of())));

        GuiItems.frame(inventory, plugin, GuiItems.themeFor(dimension), ROWS);

        return inventory;
    }

    // A lore entry may be one line or a list.
    static List<String> lore(FileConfiguration cfg, String path, Map<String, String> placeholders) {
        return cfg.isList(path)
                ? GuiItems.subList(cfg.getStringList(path), placeholders)
                : GuiItems.line(cfg.getString(path) == null ? null : GuiItems.sub(cfg.getString(path), placeholders));
    }

    private static ItemStack buildDashboardItem(DimensionToggle plugin, ToggleDimension dimension,
                                                 FileConfiguration cfg, String base) {
        plugin.getDashboardStatsManager().refreshIfStale();
        World world = dimension.findWorld();

        String dashboardBase = base + "dashboard.";
        List<String> lore = new ArrayList<>();

        lore.add(GuiItems.sub(cfg.getString(dashboardBase + "peak-players"),
                Map.of("peak", String.valueOf(plugin.getPeakPlayerManager().getPeak(dimension)))));

        if (world != null) {
            lore.add(GuiItems.sub(cfg.getString(dashboardBase + "world-border"),
                    Map.of("size", String.valueOf((long) world.getWorldBorder().getSize()))));

            int loadedChunks = plugin.getDashboardStatsManager().getLoadedChunks(dimension);
            lore.add(GuiItems.sub(cfg.getString(dashboardBase + "loaded-chunks"),
                    Map.of("chunks", loadedChunks < 0 ? "?" : String.valueOf(loadedChunks))));
            lore.add("");
            lore.add(cfg.getString(dashboardBase + "entities-header"));

            for (Map.Entry<EntityType, Integer> entry : plugin.getDashboardStatsManager().getTopEntities(dimension)) {
                lore.add(GuiItems.sub(cfg.getString(dashboardBase + "entity-line"),
                        Map.of("type", entry.getKey().name(), "count", String.valueOf(entry.getValue()))));
            }
        } else {
            lore.add(cfg.getString(dashboardBase + "world-not-loaded"));
        }

        lore.add("");
        lore.add(cfg.getString(dashboardBase + "server-wide-header"));
        double[] tps = Bukkit.getServer().getTPS();
        lore.add(GuiItems.sub(cfg.getString(dashboardBase + "tps-line"), Map.of("tps", String.format("%.2f", tps[0]))));

        return GuiItems.build(plugin, Material.FILLED_MAP, null, cfg.getString(dashboardBase + "title"), lore);
    }
}
