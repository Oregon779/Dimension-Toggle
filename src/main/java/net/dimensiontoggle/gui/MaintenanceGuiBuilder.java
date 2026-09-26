package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;

public final class MaintenanceGuiBuilder {

    private static final String[] PRESETS = {"5m", "10m", "30m", "1h", "2h", "6h"};

    private MaintenanceGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin, ToggleDimension dimension) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getFor(dimension);
        String base = "maintenance.";

        Inventory inventory = Bukkit.createInventory(null, 27,
                plugin.getMessageManager().parse(cfg.getString(base + "title", "&6&lMaintenance")));

        boolean pending = plugin.getMaintenanceManager().isPending(dimension);
        String presetLoreTemplate = cfg.getString(base + "preset-lore");

        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < PRESETS.length; i++) {
            String preset = PRESETS[i];
            inventory.setItem(slots[i], GuiItems.build(plugin, Material.CLOCK, "MAINT_START_" + preset,
                    "&6&l" + preset, List.of(GuiItems.sub(presetLoreTemplate, Map.of("preset", preset)))));
        }

        if (pending) {
            int remaining = plugin.getMaintenanceManager().getRemainingSeconds(dimension);
            inventory.setItem(13, GuiItems.build(plugin, Material.CLOCK, null,
                    cfg.getString(base + "status-active"),
                    List.of(GuiItems.sub(cfg.getString(base + "status-active-lore"), Map.of("seconds", String.valueOf(remaining)))), true));
        } else {
            inventory.setItem(13, GuiItems.build(plugin, Material.GRAY_DYE, null,
                    cfg.getString(base + "status-idle"), GuiItems.line(cfg.getString(base + "status-idle-lore"))));
        }

        if (pending) {
            int remaining = plugin.getMaintenanceManager().getRemainingSeconds(dimension);
            inventory.setItem(22, GuiItems.build(plugin, Material.REDSTONE, "MAINT_CANCEL",
                    cfg.getString(base + "cancel-button.name"),
                    GuiItems.subList(cfg.getStringList(base + "cancel-button.lore"), Map.of("seconds", String.valueOf(remaining)))));
        }

        GuiItems.fillBorderAndFiller(inventory, plugin, dimension, 3);

        inventory.setItem(18, GuiItems.build(plugin, Material.ARROW, "MAINT_BACK",
                cfg.getString(base + "back-button.name"), cfg.getStringList(base + "back-button.lore")));

        inventory.setItem(4, GuiItems.build(plugin, Material.ANVIL, "MAINT_CUSTOM",
                cfg.getString(base + "custom-button.name"), cfg.getStringList(base + "custom-button.lore")));

        return inventory;
    }
}
