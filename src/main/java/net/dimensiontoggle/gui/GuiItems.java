package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import net.dimensiontoggle.model.WorldScope;
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
        // Menu icons are signs, not tools: no "attack damage" lines under
        // the sword etc.
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

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

    // One color theme per area: the frame is the area's color with accent
    // corners, the inside is dark so the buttons stand out.
    public enum Theme {
        MAIN(Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE),
        NETHER(Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE),
        END(Material.MAGENTA_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE),
        OVERWORLD(Material.LIME_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE);

        static final Material INSIDE = Material.BLACK_STAINED_GLASS_PANE;

        final Material border;
        final Material corner;

        Theme(Material border, Material corner) {
            this.border = border;
            this.corner = corner;
        }
    }

    public static Theme themeFor(ToggleDimension dimension) {
        return dimension == ToggleDimension.NETHER ? Theme.NETHER : Theme.END;
    }

    public static Theme themeFor(WorldScope scope) {
        return switch (scope) {
            case OVERWORLD -> Theme.OVERWORLD;
            case NETHER -> Theme.NETHER;
            case END -> Theme.END;
        };
    }

    // Fills every still-empty slot: the outer edge in the theme color with
    // accent corners, everything inside dark. Slots a builder already put a
    // button in are left alone.
    public static void frame(Inventory inventory, DimensionToggle plugin, Theme theme, int rows) {
        ItemStack inside = build(plugin, Theme.INSIDE, null, " ", null);
        ItemStack border = build(plugin, theme.border, null, " ", null);
        ItemStack corner = build(plugin, theme.corner, null, " ", null);
        int lastRow = rows - 1;
        for (int slot = 0; slot < rows * 9; slot++) {
            if (inventory.getItem(slot) != null) {
                continue;
            }
            int row = slot / 9;
            int col = slot % 9;
            boolean edgeRow = row == 0 || row == lastRow;
            boolean edgeCol = col == 0 || col == 8;
            inventory.setItem(slot, edgeRow && edgeCol ? corner : edgeRow || edgeCol ? border : inside);
        }
    }

    // Slots for `count` buttons in one inner row (columns 1-7), spread so
    // the row stays symmetric. More than 7 are cut off.
    public static int[] centeredSlots(int row, int count) {
        int[] columns = switch (Math.min(count, 7)) {
            case 0 -> new int[0];
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{2, 4, 6};
            case 4 -> new int[]{1, 3, 5, 7};
            case 5 -> new int[]{2, 3, 4, 5, 6};
            case 6 -> new int[]{1, 2, 3, 5, 6, 7};
            default -> new int[]{1, 2, 3, 4, 5, 6, 7};
        };
        int[] slots = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            slots[i] = row * 9 + columns[i];
        }
        return slots;
    }

    // Slots for `count` buttons kept together around the middle column: an
    // odd count fills it, an even count leaves exactly the middle free
    // (4 -> columns 2,3 | 5,6), so every row mirrors around the same axis.
    public static int[] axisSlots(int row, int count) {
        int n = Math.min(count, count % 2 == 0 ? 6 : 7);
        int[] slots = new int[n];
        int half = n / 2;
        for (int i = 0; i < n; i++) {
            int column;
            if (n % 2 == 1) {
                column = 4 - half + i;
            } else {
                column = i < half ? 4 - half + i : 5 + (i - half);
            }
            slots[i] = row * 9 + column;
        }
        return slots;
    }

    public static void placeOnAxis(Inventory inventory, int row, List<ItemStack> items) {
        int[] slots = axisSlots(row, items.size());
        for (int i = 0; i < slots.length; i++) {
            inventory.setItem(slots[i], items.get(i));
        }
    }

    public static void place(Inventory inventory, int row, List<ItemStack> items) {
        int[] slots = centeredSlots(row, items.size());
        for (int i = 0; i < slots.length; i++) {
            inventory.setItem(slots[i], items.get(i));
        }
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
