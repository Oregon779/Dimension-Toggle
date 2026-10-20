package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;

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
        String openTime = section == null ? "?" : net.dimensiontoggle.manager.ScheduleManager.formatTime(section.get("open-time"));
        String closeTime = section == null ? "?" : net.dimensiontoggle.manager.ScheduleManager.formatTime(section.get("close-time"));

        Map<String, String> timesPlaceholders = new HashMap<>();
        timesPlaceholders.put("open-time", openTime);
        timesPlaceholders.put("close-time", closeTime);

        inventory.setItem(3, GuiItems.build(plugin, scheduleEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "SCHED_TOGGLE_ENABLED",
                guiCfg.getString(base + "toggle-button." + (scheduleEnabled ? "name-on" : "name-off")),
                GuiItems.subList(guiCfg.getStringList(base + "toggle-button.lore"), timesPlaceholders), scheduleEnabled));

        inventory.setItem(13, GuiItems.build(plugin, Material.SUNFLOWER, null,
                guiCfg.getString(base + "open-time-header"), GuiItems.subList(guiCfg.getStringList(base + "open-time-header-lore"), timesPlaceholders)));
        inventory.setItem(31, GuiItems.build(plugin, Material.CRYING_OBSIDIAN, null,
                guiCfg.getString(base + "close-time-header"), GuiItems.subList(guiCfg.getStringList(base + "close-time-header-lore"), timesPlaceholders)));

        int[] openSlots = {19, 20, 21, 22, 23, 24, 25};
        String openLoreTemplate = guiCfg.getString(base + "open-time-lore");
        for (int i = 0; i < TIME_PRESETS.length; i++) {
            boolean selected = TIME_PRESETS[i].equals(openTime);
            inventory.setItem(openSlots[i], GuiItems.build(plugin, selected ? Material.LIME_CONCRETE : Material.LIME_DYE,
                    "SCHED_OPEN_" + TIME_PRESETS[i],
                    GuiItems.sub(guiCfg.getString(base + "open-time-name", "&a%time%"), Map.of("time", TIME_PRESETS[i])),
                    List.of(GuiItems.sub(openLoreTemplate, Map.of("time", TIME_PRESETS[i]))), selected));
        }

        int[] closeSlots = {37, 38, 39, 40, 41, 42, 43};
        String closeLoreTemplate = guiCfg.getString(base + "close-time-lore");
        for (int i = 0; i < TIME_PRESETS.length; i++) {
            boolean selected = TIME_PRESETS[i].equals(closeTime);
            inventory.setItem(closeSlots[i], GuiItems.build(plugin, selected ? Material.RED_CONCRETE : Material.RED_DYE,
                    "SCHED_CLOSE_" + TIME_PRESETS[i],
                    GuiItems.sub(guiCfg.getString(base + "close-time-name", "&c%time%"), Map.of("time", TIME_PRESETS[i])),
                    List.of(GuiItems.sub(closeLoreTemplate, Map.of("time", TIME_PRESETS[i]))), selected));
        }

        inventory.setItem(5, GuiItems.build(plugin, Material.BELL, "SCHED_TOGGLE_COUNTDOWN",
                guiCfg.getString(base + "countdown-button." + (countdownEnabled ? "name-on" : "name-off")),
                guiCfg.getStringList(base + "countdown-button.lore"), countdownEnabled));

        inventory.setItem(49, GuiItems.build(plugin, Material.ARROW, "SCHED_BACK",
                guiCfg.getString(base + "back-button.name"), DimensionGuiBuilder.lore(guiCfg, base + "back-button.lore", Map.of())));

        GuiItems.frame(inventory, plugin, GuiItems.themeFor(dimension), 6);

        return inventory;
    }
}
