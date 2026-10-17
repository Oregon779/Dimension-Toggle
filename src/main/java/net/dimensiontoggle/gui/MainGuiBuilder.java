package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

public final class MainGuiBuilder {

    private MainGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getMain();

        // Only 6 real buttons - 4 rows fit them with a clear empty buffer row
        // between the two groups, and stay empty (no filler) everywhere else
        // so the icons breathe.
        Inventory inventory = Bukkit.createInventory(null, 36,
                plugin.getMessageManager().parse(cfg.getString("title", "&5&lDimensionToggle")));

        boolean netherEnabled = plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER);
        boolean endEnabled = plugin.getDimensionManager().isEnabled(ToggleDimension.END);
        boolean lockdown = plugin.getDimensionManager().isLockdownActive();

        Map<String, String> netherPlaceholders = new HashMap<>();
        netherPlaceholders.put("status", cfg.getString(netherEnabled ? "status-enabled" : "status-disabled", ""));

        Map<String, String> endPlaceholders = new HashMap<>();
        endPlaceholders.put("status", cfg.getString(endEnabled ? "status-enabled" : "status-disabled", ""));

        inventory.setItem(11, GuiItems.build(plugin, Material.NETHERRACK, "MAIN_OPEN_NETHER",
                GuiItems.sub(cfg.getString("nether-head.name"), netherPlaceholders),
                GuiItems.subList(cfg.getStringList("nether-head.lore"), netherPlaceholders), netherEnabled));

        inventory.setItem(4, GuiItems.build(plugin, Material.NETHER_STAR, null,
                cfg.getString("emblem.name"), cfg.getStringList("emblem.lore")));

        // Overworld & performance tools - the Overworld itself is never disabled.
        Map<String, String> overworldPlaceholders = Map.of(
                "tps", String.format("%.1f", plugin.getPerformanceMonitor().latest().tps1()),
                "mspt", String.format("%.1f", plugin.getPerformanceMonitor().latest().mspt()));
        inventory.setItem(13, GuiItems.build(plugin, Material.GRASS_BLOCK, "MAIN_OPEN_OVERWORLD",
                GuiItems.sub(cfg.getString("overworld-head.name", "&a&lOverworld"), overworldPlaceholders),
                GuiItems.subList(cfg.getStringList("overworld-head.lore"), overworldPlaceholders)));

        inventory.setItem(15, GuiItems.build(plugin, Material.END_STONE, "MAIN_OPEN_END",
                GuiItems.sub(cfg.getString("end-head.name"), endPlaceholders),
                GuiItems.subList(cfg.getStringList("end-head.lore"), endPlaceholders), endEnabled));

        String lockdownName = cfg.getString(lockdown ? "lockdown-button.name-active" : "lockdown-button.name-inactive");
        var lockdownLore = cfg.getStringList(lockdown ? "lockdown-button.lore-active" : "lockdown-button.lore-inactive");
        inventory.setItem(31, GuiItems.build(plugin, lockdown ? Material.RED_CONCRETE : Material.BARRIER, "MAIN_LOCKDOWN",
                lockdownName, lockdownLore, lockdown));

        inventory.setItem(35, GuiItems.build(plugin, Material.OAK_DOOR, "MAIN_CLOSE",
                cfg.getString("close-button.name"), cfg.getStringList("close-button.lore")));

        return inventory;
    }
}
