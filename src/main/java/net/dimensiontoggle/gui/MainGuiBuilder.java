package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class MainGuiBuilder {

    private MainGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getMain();

        Inventory inventory = Bukkit.createInventory(null, 45,
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
                GuiItems.subList(cfg.getStringList("nether-head.lore"), netherPlaceholders)));

        inventory.setItem(13, GuiItems.build(plugin, Material.NETHER_STAR, null,
                cfg.getString("emblem.name"), cfg.getStringList("emblem.lore")));

        inventory.setItem(15, GuiItems.build(plugin, Material.END_STONE, "MAIN_OPEN_END",
                GuiItems.sub(cfg.getString("end-head.name"), endPlaceholders),
                GuiItems.subList(cfg.getStringList("end-head.lore"), endPlaceholders)));

        String lockdownName = cfg.getString(lockdown ? "lockdown-button.name-active" : "lockdown-button.name-inactive");
        var lockdownLore = cfg.getStringList(lockdown ? "lockdown-button.lore-active" : "lockdown-button.lore-inactive");
        inventory.setItem(40, GuiItems.build(plugin, lockdown ? Material.RED_CONCRETE : Material.BARRIER, "MAIN_LOCKDOWN",
                lockdownName, lockdownLore));

        inventory.setItem(44, GuiItems.build(plugin, Material.OAK_DOOR, "MAIN_CLOSE",
                cfg.getString("close-button.name"), cfg.getStringList("close-button.lore")));

        ItemStack baseFiller = GuiItems.build(plugin, Material.BLACK_STAINED_GLASS_PANE, null, " ", null);
        for (int slot = 0; slot < 45; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, baseFiller);
            }
        }

        return inventory;
    }
}
