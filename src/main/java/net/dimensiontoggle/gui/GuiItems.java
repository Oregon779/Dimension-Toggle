package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

public final class GuiItems {

    private static NamespacedKey actionKey(DimensionToggle plugin) {
        return new NamespacedKey(plugin, "gui-action");
    }

    private GuiItems() {
    }

    public static ItemStack build(DimensionToggle plugin, Material material, String action,
                                   String rawName, List<String> rawLore) {
        return build(plugin, material, action, rawName, rawLore, false);
    }

    // Same as build(), plus an enchant glint (with the enchantment name hidden)
    // used across the editor to mark whichever state is currently "on" -
    // a glowing icon reads as active at a glance, without needing a legend.
    public static ItemStack build(DimensionToggle plugin, Material material, String action,
                                   String rawName, List<String> rawLore, boolean glowing) {
        ItemStack item = new ItemStack(material);
        applyMetaAndAction(plugin, item, action, rawName, rawLore);
        if (glowing) {
            applyGlow(item);
        }
        return item;
    }

    private static void applyGlow(ItemStack item) {
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
    }

    public static ItemStack buildPlayerHead(DimensionToggle plugin, OfflinePlayer owner, String action,
                                             String rawName, List<String> rawLore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(owner);
            item.setItemMeta(skullMeta);
        }
        applyMetaAndAction(plugin, item, action, rawName, rawLore);
        return item;
    }

    private static void applyMetaAndAction(DimensionToggle plugin, ItemStack item, String action,
                                            String rawName, List<String> rawLore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        if (rawName != null) {
            meta.displayName(plugin.getMessageManager().parse(rawName).decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        if (rawLore != null && !rawLore.isEmpty()) {
            List<Component> lore = rawLore.stream()
                    .map(line -> plugin.getMessageManager().parse(line)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                    .toList();
            meta.lore(lore);
        }

        if (action != null) {
            meta.getPersistentDataContainer().set(actionKey(plugin), PersistentDataType.STRING, action);
        }

        item.setItemMeta(meta);
    }

    public static org.bukkit.Material resolveMobIcon(org.bukkit.entity.EntityType type) {
        try {
            return org.bukkit.Material.valueOf(type.name() + "_HEAD");
        } catch (IllegalArgumentException ignored) {

        }
        try {
            return org.bukkit.Material.valueOf(type.name() + "_SKULL");
        } catch (IllegalArgumentException ignored) {

        }
        try {
            return org.bukkit.Material.valueOf(type.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException ignored) {
            return org.bukkit.Material.PAPER;
        }
    }

    public static Material fillerFor(ToggleDimension dimension) {
        return dimension == ToggleDimension.NETHER
                ? Material.BLACK_STAINED_GLASS_PANE
                : Material.PURPLE_STAINED_GLASS_PANE;
    }

    public static Material borderFor(ToggleDimension dimension) {
        return dimension == ToggleDimension.NETHER
                ? Material.ORANGE_STAINED_GLASS_PANE
                : Material.MAGENTA_STAINED_GLASS_PANE;
    }

    // Fills every still-empty slot, using the border material on the true
    // outer edge (top row, bottom row, left/right columns) and the plain
    // filler everywhere else - a cheap two-tone "frame" instead of one flat
    // color, without touching any slot a builder already placed a button in.
    public static void fillBorderAndFiller(Inventory inventory, DimensionToggle plugin,
                                            Material fillerMaterial, Material borderMaterial, int rows) {
        ItemStack fillerItem = build(plugin, fillerMaterial, null, " ", null);
        ItemStack borderItem = build(plugin, borderMaterial, null, " ", null);
        int lastRow = rows - 1;
        int size = rows * 9;

        for (int slot = 0; slot < size; slot++) {
            if (inventory.getItem(slot) != null) {
                continue;
            }
            int row = slot / 9;
            int col = slot % 9;
            boolean isBorder = row == 0 || row == lastRow || col == 0 || col == 8;
            inventory.setItem(slot, isBorder ? borderItem : fillerItem);
        }
    }

    public static void fillBorderAndFiller(Inventory inventory, DimensionToggle plugin,
                                            ToggleDimension dimension, int rows) {
        fillBorderAndFiller(inventory, plugin, fillerFor(dimension), borderFor(dimension), rows);
    }

    public static String readAction(DimensionToggle plugin, ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(actionKey(plugin), PersistentDataType.STRING);
    }

    // A single configured lore line. List.of(null) throws, and a key missing
    // from an older user gui config (the update merge only adds top-level
    // sections) must mean "no lore" - not a menu that fails on every refresh.
    public static List<String> line(String raw) {
        return raw == null ? List.of() : List.of(raw);
    }

    public static String sub(String raw, Map<String, String> placeholders) {
        if (raw == null) {
            return "";
        }
        String result = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    public static List<String> subList(List<String> raw, Map<String, String> placeholders) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(line -> sub(line, placeholders)).toList();
    }
}
