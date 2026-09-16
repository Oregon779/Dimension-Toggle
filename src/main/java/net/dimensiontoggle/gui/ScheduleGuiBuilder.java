package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScheduleGuiBuilder {

    private static final String[] TIME_PRESETS = {"06:00", "08:00", "12:00", "18:00", "20:00", "22:00", "00:00"};

    private ScheduleGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin, ToggleDimension dimension) {
        FileConfiguration guiCfg = plugin.getGuiConfigManager().getFor(dimension);
        String base = "schedule.";

        Inventory inventory = Bukkit.createInventory(null, 54,
                plugin.getMessageManager().parse(guiCfg.getString(base + "title", "&b&lSchedule")));

        var section = plugin.getConfigManager().getConfig().getConfigurationSection("schedule." + dimension.getKey());
        boolean scheduleEnabled = section != null && section.getBoolean("enabled", false);
        boolean countdownEnabled = section != null && section.getBoolean("countdown-enabled", true);
        String openTime = section == null ? "?" : section.getString("open-time", "08:00");
        String closeTime = section == null ? "?" : section.getString("close-time", "22:00");

        Map<String, String> timesPlaceholders = new HashMap<>();
        timesPlaceholders.put("open-time", openTime);
        timesPlaceholders.put("close-time", closeTime);

        inventory.setItem(4, GuiItems.build(plugin, scheduleEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "SCHED_TOGGLE_ENABLED",
                guiCfg.getString(base + "toggle-button." + (scheduleEnabled ? "name-on" : "name-off")),
                GuiItems.subList(guiCfg.getStringList(base + "toggle-button.lore"), timesPlaceholders)));

        inventory.setItem(20, GuiItems.build(plugin, Material.YELLOW_STAINED_GLASS_PANE, null,
                guiCfg.getString(base + "open-time-header"), null));
        inventory.setItem(24, GuiItems.build(plugin, Material.ORANGE_STAINED_GLASS_PANE, null,
                guiCfg.getString(base + "close-time-header"), null));

        int[] openSlots = {28, 29, 30, 31, 32, 33, 34};
        String openLoreTemplate = guiCfg.getString(base + "open-time-lore");
        for (int i = 0; i < TIME_PRESETS.length; i++) {
            inventory.setItem(openSlots[i], GuiItems.build(plugin, Material.LIME_DYE, "SCHED_OPEN_" + TIME_PRESETS[i],
                    "&a" + TIME_PRESETS[i], List.of(GuiItems.sub(openLoreTemplate, Map.of("time", TIME_PRESETS[i])))));
        }

        int[] closeSlots = {37, 38, 39, 40, 41, 42, 43};
        String closeLoreTemplate = guiCfg.getString(base + "close-time-lore");
        for (int i = 0; i < TIME_PRESETS.length; i++) {
            inventory.setItem(closeSlots[i], GuiItems.build(plugin, Material.RED_DYE, "SCHED_CLOSE_" + TIME_PRESETS[i],
                    "&c" + TIME_PRESETS[i], List.of(GuiItems.sub(closeLoreTemplate, Map.of("time", TIME_PRESETS[i])))));
        }

        inventory.setItem(49, GuiItems.build(plugin, Material.BELL, "SCHED_TOGGLE_COUNTDOWN",
                guiCfg.getString(base + "countdown-button." + (countdownEnabled ? "name-on" : "name-off")),
                guiCfg.getStringList(base + "countdown-button.lore")));

        ItemStack filler = GuiItems.build(plugin, GuiItems.fillerFor(dimension), null, " ", null);
        for (int slot = 0; slot < 54; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }

        inventory.setItem(45, GuiItems.build(plugin, Material.ARROW, "SCHED_BACK",
                guiCfg.getString(base + "back-button.name"), guiCfg.getStringList(base + "back-button.lore")));

        return inventory;
    }
}
