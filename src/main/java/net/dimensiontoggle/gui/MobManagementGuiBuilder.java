package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.manager.MobManagementManager;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MobManagementGuiBuilder {

    private MobManagementGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin, ToggleDimension dimension) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getFor(dimension);
        String base = "mob-management.";

        Inventory inventory = Bukkit.createInventory(null, 54,
                plugin.getMessageManager().parse(cfg.getString(base + "title", "&2&lMob Management")));

        List<EntityType> mobs = MobManagementManager.mobsFor(dimension);
        MobManagementManager mobManager = plugin.getMobManagementManager();

        int slot = 10;
        for (EntityType type : mobs) {
            if (slot % 9 == 8) {
                slot += 2;
            }

            boolean spawnEnabled = mobManager.isSpawnEnabled(dimension, type);
            int cleanupMinutes = mobManager.getCleanupMinutes(dimension, type);

            String mobName = prettifyName(type.name());
            String nameTemplate = cfg.getString(base + (spawnEnabled ? "mob-name-enabled" : "mob-name-disabled"));

            List<String> lore = new ArrayList<>();
            String statusLine = cfg.getString(base + (spawnEnabled ? "status-spawning-allowed" : "status-spawning-blocked"));
            lore.add(GuiItems.sub(statusLine, Map.of()));

            String cleanupLine = cleanupMinutes <= 0
                    ? cfg.getString(base + "cleanup-off")
                    : GuiItems.sub(cfg.getString(base + "cleanup-active"), Map.of("minutes", String.valueOf(cleanupMinutes)));
            lore.add(cleanupLine);

            if (cleanupMinutes > 0) {
                int nextIn = plugin.getMobManagementManager().getMinutesUntilNextCleanup(dimension, type);
                lore.add(GuiItems.sub(cfg.getString(base + "cleanup-next"), Map.of("next", String.valueOf(nextIn))));
            }
            lore.add("");
            lore.addAll(cfg.getStringList(base + "mob-lore-hint"));

            Material icon = GuiItems.resolveMobIcon(type);
            inventory.setItem(slot, GuiItems.build(plugin, icon, "MOB_TOGGLE_" + type.name(),
                    GuiItems.sub(nameTemplate, Map.of("mob", mobName)), lore, spawnEnabled));

            slot++;
        }

        inventory.setItem(49, GuiItems.build(plugin, Material.ARROW, "MOB_BACK",
                cfg.getString(base + "back-button.name"), cfg.getStringList(base + "back-button.lore")));

        GuiItems.fillBorderAndFiller(inventory, plugin, dimension, 6);

        return inventory;
    }

    private static String prettifyName(String enumName) {
        String[] words = enumName.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return result.toString();
    }
}
