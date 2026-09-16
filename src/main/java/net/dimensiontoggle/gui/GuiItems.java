package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
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
        ItemStack item = new ItemStack(material);
        applyMetaAndAction(plugin, item, action, rawName, rawLore);
        return item;
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

    public static org.bukkit.Material fillerFor(net.dimensiontoggle.model.ToggleDimension dimension) {
        return dimension == net.dimensiontoggle.model.ToggleDimension.NETHER
                ? org.bukkit.Material.BLACK_STAINED_GLASS_PANE
                : org.bukkit.Material.PURPLE_STAINED_GLASS_PANE;
    }

    public static String readAction(DimensionToggle plugin, ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(actionKey(plugin), PersistentDataType.STRING);
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
