package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.manager.MobManagementManager;
import net.dimensiontoggle.model.WorldScope;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// The Overworld has ~80 mobs: first pick a group, then the (paged) list.
public final class MobCategoryGuiBuilder {

    static final int ROWS = 3;
    static final int BACK_SLOT = 22;

    private MobCategoryGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getOverworld();
        String base = "mob-categories.";
        Inventory inventory = Bukkit.createInventory(null, ROWS * 9,
                plugin.getMessageManager().parse(cfg.getString(base + "title", "&a&lMob Management")));

        MobManagementManager mobs = plugin.getMobManagementManager();
        List<ItemStack> items = new ArrayList<>();
        for (MobManagementManager.Category category : MobManagementManager.Category.values()) {
            int allowed = 0;
            int cleanups = 0;
            for (EntityType type : category.mobs()) {
                if (mobs.isSpawnEnabled(WorldScope.OVERWORLD, type)) {
                    allowed++;
                }
                if (mobs.getCleanupMinutes(WorldScope.OVERWORLD, type) > 0) {
                    cleanups++;
                }
            }
            Map<String, String> p = Map.of(
                    "total", String.valueOf(category.mobs().size()),
                    "allowed", String.valueOf(allowed),
                    "blocked", String.valueOf(category.mobs().size() - allowed),
                    "cleanups", String.valueOf(cleanups));
            String path = base + category.key();
            items.add(GuiItems.build(plugin, icon(category), "MOBCAT_" + category.name(),
                    GuiItems.sub(cfg.getString(path + ".name", category.key()), p),
                    GuiItems.subList(cfg.getStringList(path + ".lore"), p)));
        }
        GuiItems.place(inventory, 1, items);

        inventory.setItem(BACK_SLOT, GuiItems.build(plugin, Material.ARROW, "MOBCAT_BACK",
                cfg.getString(base + "back-button.name"), DimensionGuiBuilder.lore(cfg, base + "back-button.lore", Map.of())));

        GuiItems.frame(inventory, plugin, GuiItems.Theme.OVERWORLD, ROWS);
        return inventory;
    }

    private static Material icon(MobManagementManager.Category category) {
        return switch (category) {
            case MONSTERS -> Material.CREEPER_HEAD;
            case ANIMALS -> Material.COW_SPAWN_EGG;
            case WATER -> Material.AXOLOTL_BUCKET;
            case OTHER -> Material.VILLAGER_SPAWN_EGG;
        };
    }
}
