package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.manager.MobManagementManager;
import net.dimensiontoggle.model.WorldScope;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// One page of mobs (28 per page, the inner 4x7 area). Nether and End fit on
// one page; the Overworld list is split into categories and pages.
public final class MobManagementGuiBuilder {

    static final int ROWS = 6;
    static final int PER_PAGE = 28;
    static final int BACK_SLOT = 49;
    static final int PREVIOUS_SLOT = 48;
    static final int NEXT_SLOT = 50;

    private MobManagementGuiBuilder() {
    }

    public static int pageCount(List<EntityType> mobs) {
        return Math.max(1, (mobs.size() + PER_PAGE - 1) / PER_PAGE);
    }

    public static List<EntityType> mobsOf(WorldScope scope, MobManagementManager.Category category) {
        return category == null ? MobManagementManager.mobsFor(scope) : category.mobs();
    }

    public static Inventory build(DimensionToggle plugin, WorldScope scope, MobManagementManager.Category category, int page) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getFor(scope);
        String base = "mob-management.";
        List<EntityType> mobs = mobsOf(scope, category);
        int pages = pageCount(mobs);
        int current = Math.clamp(page, 0, pages - 1);

        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("category", category == null ? ""
                : cfg.getString("mob-categories." + category.key() + ".title-word", category.key()));
        titlePlaceholders.put("page", String.valueOf(current + 1));
        titlePlaceholders.put("pages", String.valueOf(pages));
        String titleKey = category != null ? base + "category-title" : base + "title";
        Inventory inventory = Bukkit.createInventory(null, ROWS * 9, plugin.getMessageManager().parse(
                GuiItems.sub(cfg.getString(titleKey, cfg.getString(base + "title", "&2&lMob Management")), titlePlaceholders)));

        MobManagementManager mobManager = plugin.getMobManagementManager();
        int from = current * PER_PAGE;
        int to = Math.min(mobs.size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            EntityType type = mobs.get(i);
            int index = i - from;
            int slot = (1 + index / 7) * 9 + 1 + index % 7;

            boolean spawnEnabled = mobManager.isSpawnEnabled(scope, type);
            int cleanupMinutes = mobManager.getCleanupMinutes(scope, type);
            Map<String, String> p = new HashMap<>();
            p.put("mob", prettifyName(type.name()));
            p.put("minutes", String.valueOf(cleanupMinutes));
            p.put("interval", plugin.getMessageManager().formatDuration(Math.max(0, cleanupMinutes) * 60));

            List<String> lore = new ArrayList<>();
            lore.add(cfg.getString(base + (spawnEnabled ? "status-spawning-allowed" : "status-spawning-blocked"), ""));
            lore.add(GuiItems.sub(cfg.getString(base + (cleanupMinutes <= 0 ? "cleanup-off" : "cleanup-active"), ""), p));
            if (cleanupMinutes > 0) {
                int nextIn = mobManager.getMinutesUntilNextCleanup(scope, type);
                p.put("next", plugin.getMessageManager().formatDuration(Math.max(0, nextIn) * 60));
                lore.add(GuiItems.sub(cfg.getString(base + "cleanup-next", ""), p));
            }
            lore.addAll(GuiItems.subList(cfg.getStringList(base + "mob-lore-hint"), p));

            String nameTemplate = cfg.getString(base + (spawnEnabled ? "mob-name-enabled" : "mob-name-disabled"));
            inventory.setItem(slot, GuiItems.build(plugin, GuiItems.resolveMobIcon(type), "MOB_TOGGLE_" + type.name(),
                    GuiItems.sub(nameTemplate, p), lore, spawnEnabled));
        }

        Map<String, String> pagePlaceholders = Map.of("page", String.valueOf(current + 1), "pages", String.valueOf(pages));
        if (current > 0) {
            inventory.setItem(PREVIOUS_SLOT, GuiItems.build(plugin, Material.SPECTRAL_ARROW, "MOB_PAGE_PREVIOUS",
                    GuiItems.sub(cfg.getString(base + "previous-page.name", "&e« Previous page"), pagePlaceholders),
                    GuiItems.subList(cfg.getStringList(base + "previous-page.lore"), pagePlaceholders)));
        }
        if (current < pages - 1) {
            inventory.setItem(NEXT_SLOT, GuiItems.build(plugin, Material.SPECTRAL_ARROW, "MOB_PAGE_NEXT",
                    GuiItems.sub(cfg.getString(base + "next-page.name", "&eNext page »"), pagePlaceholders),
                    GuiItems.subList(cfg.getStringList(base + "next-page.lore"), pagePlaceholders)));
        }

        inventory.setItem(BACK_SLOT, GuiItems.build(plugin, Material.ARROW, "MOB_BACK",
                cfg.getString(base + "back-button.name"), DimensionGuiBuilder.lore(cfg, base + "back-button.lore", Map.of())));

        GuiItems.frame(inventory, plugin, GuiItems.themeFor(scope), ROWS);
        return inventory;
    }

    static String prettifyName(String enumName) {
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
