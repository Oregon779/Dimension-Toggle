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

public final class DimensionGuiBuilder {

    private static final int DASHBOARD_SLOT = 31;

    private static final int[] INTERIOR_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30,  32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private DimensionGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin, ToggleDimension dimension) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getFor(dimension);
        String base = "control-panel.";

        Inventory inventory = Bukkit.createInventory(null, 54,
                plugin.getMessageManager().parse(cfg.getString(base + "title", "&5&lControl Panel")));

        boolean enabled = plugin.getDimensionManager().isEnabled(dimension);
        boolean locked = plugin.getDimensionManager().isLocked(dimension);
        boolean keepInv = plugin.getKeepInventoryManager().isEnabled(dimension);
        int playerCount = plugin.getDimensionManager().countPlayersInDimension(dimension);

        var limitSection = plugin.getConfigManager().getConfig().getConfigurationSection("limits." + dimension.getKey());
        boolean limitEnabled = limitSection != null && limitSection.getBoolean("enabled", false);
        int limit = limitSection == null ? 0 : limitSection.getInt("max-players", 0);

        Map<String, String> actionPlaceholder = Map.of("action", cfg.getString(enabled ? "action-disable" : "action-enable", ""));
        inventory.setItem(13, GuiItems.build(plugin, enabled ? Material.LIME_DYE : Material.GRAY_DYE, "DIM_TOGGLE",
                cfg.getString(base + "toggle-button." + (enabled ? "name-enabled" : "name-disabled")),
                List.of(GuiItems.sub(cfg.getString(base + "toggle-button.lore"), actionPlaceholder))));

        List<ItemStack> features = new ArrayList<>();

        Map<String, String> lockActionPlaceholder = Map.of("action", cfg.getString(locked ? "action-unlock" : "action-lock", ""));
        features.add(GuiItems.build(plugin, locked ? Material.IRON_BARS : Material.OAK_FENCE_GATE, "DIM_SOFTLOCK",
                cfg.getString(base + "softlock-button." + (locked ? "name-locked" : "name-unlocked")),
                GuiItems.subList(cfg.getStringList(base + "softlock-button.lore"), lockActionPlaceholder)));

        Map<String, String> countPlaceholder = Map.of("count", String.valueOf(playerCount));
        features.add(GuiItems.build(plugin, Material.PLAYER_HEAD, "DIM_PLAYERLIST",
                GuiItems.sub(cfg.getString(base + "playerlist-button.name"), countPlaceholder),
                cfg.getStringList(base + "playerlist-button.lore")));

        Map<String, String> limitPlaceholders = new HashMap<>();
        limitPlaceholders.put("limit-status", cfg.getString(limitEnabled ? "limit-status-enabled" : "limit-status-disabled", ""));
        limitPlaceholders.put("limit", String.valueOf(limit));
        features.add(GuiItems.build(plugin, Material.PLAYER_HEAD, "DIM_LIMIT",
                cfg.getString(base + "limit-button.name"),
                GuiItems.subList(cfg.getStringList(base + "limit-button.lore"), limitPlaceholders)));

        features.add(GuiItems.build(plugin, keepInv ? Material.TOTEM_OF_UNDYING : Material.BARRIER, "DIM_KEEPINV",
                cfg.getString(base + "keepinventory-button." + (keepInv ? "name-on" : "name-off")),
                cfg.getStringList(base + "keepinventory-button.lore")));

        features.add(GuiItems.build(plugin, Material.CLOCK, "DIM_MAINTENANCE",
                cfg.getString(base + "maintenance-button.name"), cfg.getStringList(base + "maintenance-button.lore")));

        features.add(GuiItems.build(plugin, Material.RECOVERY_COMPASS, "DIM_SCHEDULE",
                cfg.getString(base + "schedule-button.name"), cfg.getStringList(base + "schedule-button.lore")));

        features.add(GuiItems.build(plugin, Material.ZOMBIE_HEAD, "DIM_MOBMANAGEMENT",
                cfg.getString(base + "mobmanagement-button.name"), cfg.getStringList(base + "mobmanagement-button.lore")));

        boolean mobSpawningEnabled = plugin.getMobSpawnManager().isEnabled(dimension);
        features.add(GuiItems.build(plugin, mobSpawningEnabled ? Material.SPAWNER : Material.BARRIER, "DIM_MOBSPAWN",
                cfg.getString(base + "mobspawn-button." + (mobSpawningEnabled ? "name-on" : "name-off")),
                cfg.getStringList(base + "mobspawn-button.lore")));

        features.add(GuiItems.build(plugin, Material.WHITE_STAINED_GLASS, "DIM_WORLDBORDER",
                cfg.getString(base + "worldborder-button.name"), cfg.getStringList(base + "worldborder-button.lore")));

        boolean pvpAvailable = plugin.getPvpIntegrationManager().isAvailable();
        boolean pvpEnabled = pvpAvailable && plugin.getPvpIntegrationManager().isPvpEnabled(dimension);
        Material pvpMaterial = !pvpAvailable ? Material.BARRIER : pvpEnabled ? Material.IRON_SWORD : Material.WOODEN_HOE;
        String pvpKey = !pvpAvailable ? "name-unavailable" : pvpEnabled ? "name-on" : "name-off";
        features.add(GuiItems.build(plugin, pvpMaterial, pvpAvailable ? "DIM_PVP" : null,
                cfg.getString(base + "pvp-button." + pvpKey), cfg.getStringList(base + "pvp-button.lore-" + (pvpAvailable ? "available" : "unavailable"))));

        if (dimension == ToggleDimension.END) {
            boolean elytra = plugin.getElytraFlyManager().isEnabled();
            boolean gatewaysBlocked = plugin.getConfigManager().getConfig().getBoolean("block-end-gateways", true);

            features.add(GuiItems.build(plugin, elytra ? Material.ELYTRA : Material.BARRIER, "DIM_ELYTRA",
                    cfg.getString(base + "elytra-button." + (elytra ? "name-on" : "name-off")),
                    cfg.getStringList(base + "elytra-button.lore")));

            features.add(GuiItems.build(plugin, gatewaysBlocked ? Material.BARRIER : Material.ENDER_EYE, "DIM_GATEWAY",
                    cfg.getString(base + "gateway-button." + (gatewaysBlocked ? "name-blocked" : "name-allowed")),
                    cfg.getStringList(base + "gateway-button.lore")));
        } else {
            boolean spawnersEnabled = plugin.getSpawnerToggleManager().isEnabled(dimension);

            features.add(GuiItems.build(plugin, spawnersEnabled ? Material.SPAWNER : Material.BARRIER, "DIM_SPAWNERS",
                    cfg.getString(base + "spawners-button." + (spawnersEnabled ? "name-on" : "name-off")),
                    cfg.getStringList(base + "spawners-button.lore")));
        }

        for (int i = 0; i < features.size() && i < INTERIOR_SLOTS.length; i++) {
            inventory.setItem(INTERIOR_SLOTS[i], features.get(i));
        }

        inventory.setItem(DASHBOARD_SLOT, buildDashboardItem(plugin, dimension, cfg, base));

        inventory.setItem(49, GuiItems.build(plugin, Material.ARROW, "DIM_BACK",
                cfg.getString(base + "back-button.name"), cfg.getStringList(base + "back-button.lore")));

        ItemStack filler = GuiItems.build(plugin, GuiItems.fillerFor(dimension), null, " ", null);
        for (int slot = 0; slot < 54; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }

        return inventory;
    }

    private static ItemStack buildDashboardItem(DimensionToggle plugin, ToggleDimension dimension,
                                                 FileConfiguration cfg, String base) {
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

        return GuiItems.build(plugin, Material.MAP, null, cfg.getString(dashboardBase + "title"), lore);
    }
}
