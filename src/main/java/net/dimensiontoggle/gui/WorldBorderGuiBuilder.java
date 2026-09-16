package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class WorldBorderGuiBuilder {

    private static final long[] PRESETS = {1_000, 5_000, 10_000, 60_000_000};
    private static final Material[] PRESET_MATERIALS = {
            Material.GREEN_CONCRETE, Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.RED_CONCRETE
    };

    private WorldBorderGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin, ToggleDimension dimension) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getFor(dimension);
        String base = "worldborder.";

        Inventory inventory = Bukkit.createInventory(null, 27,
                plugin.getMessageManager().parse(cfg.getString(base + "title", "&b&lWorld Border")));

        World world = dimension.findWorld();
        long currentSize = world == null ? 0 : (long) world.getWorldBorder().getSize();
        double centerX = world == null ? 0 : world.getWorldBorder().getCenter().getX();
        double centerZ = world == null ? 0 : world.getWorldBorder().getCenter().getZ();

        Map<String, String> sizePlaceholders = Map.of(
                "size", String.valueOf(currentSize),
                "center-x", String.valueOf((long) centerX),
                "center-z", String.valueOf((long) centerZ));

        inventory.setItem(13, GuiItems.build(plugin, Material.FILLED_MAP, "WB_ADJUST",
                cfg.getString(base + "adjust-button.name"),
                GuiItems.subList(cfg.getStringList(base + "adjust-button.lore"), sizePlaceholders)));

        int[] presetSlots = {20, 21, 23, 24};
        for (int i = 0; i < PRESETS.length; i++) {
            inventory.setItem(presetSlots[i], GuiItems.build(plugin, PRESET_MATERIALS[i],
                    "WB_PRESET_" + PRESETS[i],
                    GuiItems.sub(cfg.getString(base + "preset-name"), Map.of("size", String.valueOf(PRESETS[i]))),
                    List.of(GuiItems.sub(cfg.getString(base + "preset-lore"), Map.of("size", String.valueOf(PRESETS[i]))))));
        }

        inventory.setItem(22, GuiItems.build(plugin, Material.COMPASS, "WB_CENTER",
                cfg.getString(base + "center-button.name"), cfg.getStringList(base + "center-button.lore")));

        ItemStack filler = GuiItems.build(plugin, GuiItems.fillerFor(dimension), null, " ", null);
        for (int slot = 0; slot < 27; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }

        inventory.setItem(18, GuiItems.build(plugin, Material.ARROW, "WB_BACK",
                cfg.getString(base + "back-button.name"), cfg.getStringList(base + "back-button.lore")));

        return inventory;
    }
}
