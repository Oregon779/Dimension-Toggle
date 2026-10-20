package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import net.dimensiontoggle.performance.PerformanceMonitor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

// Five rows: emblem on top, the three areas in the middle, server info /
// lockdown / close at the bottom.
public final class MainGuiBuilder {

    static final int ROWS = 5;
    static final int NETHER_SLOT = 20;
    static final int OVERWORLD_SLOT = 22;
    static final int END_SLOT = 24;
    static final int INFO_SLOT = 38;
    static final int LOCKDOWN_SLOT = 40;
    static final int CLOSE_SLOT = 42;

    private MainGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getMain();

        Inventory inventory = Bukkit.createInventory(null, ROWS * 9,
                plugin.getMessageManager().parse(cfg.getString("title", "&5&lDimensionToggle")));

        boolean netherEnabled = plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER);
        boolean endEnabled = plugin.getDimensionManager().isEnabled(ToggleDimension.END);
        boolean lockdown = plugin.getDimensionManager().isLockdownActive();
        PerformanceMonitor.Snapshot snapshot = plugin.getPerformanceMonitor().latest();

        inventory.setItem(4, GuiItems.build(plugin, Material.NETHER_STAR, null,
                cfg.getString("emblem.name"), cfg.getStringList("emblem.lore"), true));

        inventory.setItem(NETHER_SLOT, GuiItems.build(plugin, Material.NETHERRACK, "MAIN_OPEN_NETHER",
                cfg.getString("nether-head.name"),
                GuiItems.subList(cfg.getStringList("nether-head.lore"), dimensionPlaceholders(plugin, cfg, ToggleDimension.NETHER)),
                netherEnabled));

        // Overworld & performance tools - the Overworld itself is never disabled.
        Map<String, String> overworldPlaceholders = Map.of(
                "tps", String.format("%.1f", snapshot.tps1()),
                "mspt", String.format("%.1f", snapshot.mspt()));
        inventory.setItem(OVERWORLD_SLOT, GuiItems.build(plugin, Material.GRASS_BLOCK, "MAIN_OPEN_OVERWORLD",
                GuiItems.sub(cfg.getString("overworld-head.name", "&a&lOverworld"), overworldPlaceholders),
                GuiItems.subList(cfg.getStringList("overworld-head.lore"), overworldPlaceholders)));

        inventory.setItem(END_SLOT, GuiItems.build(plugin, Material.END_STONE, "MAIN_OPEN_END",
                cfg.getString("end-head.name"),
                GuiItems.subList(cfg.getStringList("end-head.lore"), dimensionPlaceholders(plugin, cfg, ToggleDimension.END)),
                endEnabled));

        Map<String, String> info = new HashMap<>();
        info.put("online", String.valueOf(snapshot.online()));
        info.put("max", String.valueOf(snapshot.maxPlayers()));
        info.put("tps", String.format("%.1f", snapshot.tps1()));
        info.put("mspt", String.format("%.1f", snapshot.mspt()));
        info.put("version", plugin.getPluginMeta().getVersion());
        inventory.setItem(INFO_SLOT, GuiItems.build(plugin, Material.SPYGLASS, null,
                cfg.getString("info-button.name"), GuiItems.subList(cfg.getStringList("info-button.lore"), info)));

        String lockdownName = cfg.getString(lockdown ? "lockdown-button.name-active" : "lockdown-button.name-inactive");
        var lockdownLore = cfg.getStringList(lockdown ? "lockdown-button.lore-active" : "lockdown-button.lore-inactive");
        inventory.setItem(LOCKDOWN_SLOT, GuiItems.build(plugin, lockdown ? Material.RED_CONCRETE : Material.BARRIER,
                "MAIN_LOCKDOWN", lockdownName, lockdownLore, lockdown));

        inventory.setItem(CLOSE_SLOT, GuiItems.build(plugin, Material.OAK_DOOR, "MAIN_CLOSE",
                cfg.getString("close-button.name"), cfg.getStringList("close-button.lore")));

        GuiItems.frame(inventory, plugin, GuiItems.Theme.MAIN, ROWS);
        return inventory;
    }

    private static Map<String, String> dimensionPlaceholders(DimensionToggle plugin, FileConfiguration cfg,
                                                             ToggleDimension dimension) {
        Map<String, String> placeholders = new HashMap<>();
        boolean enabled = plugin.getDimensionManager().isEnabled(dimension);
        boolean locked = plugin.getDimensionManager().isLocked(dimension);
        placeholders.put("status", cfg.getString(!enabled ? "status-disabled" : locked ? "status-locked" : "status-enabled", ""));
        placeholders.put("players", String.valueOf(plugin.getDimensionManager().countPlayersInDimension(dimension)));
        return placeholders;
    }
}
