package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

public final class PlayerListGuiBuilder {

    private PlayerListGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin, ToggleDimension dimension) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getFor(dimension);
        String base = "playerlist.";

        Inventory inventory = Bukkit.createInventory(null, 54,
                plugin.getMessageManager().parse(cfg.getString(base + "title", "&b&lPlayers")));

        List<Player> players = plugin.getDimensionManager().getPlayersInDimension(dimension);

        int slot = 0;
        for (Player player : players) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, GuiItems.buildPlayerHead(plugin, player, "PLIST_TP_" + player.getUniqueId(),
                    GuiItems.sub(cfg.getString(base + "head-name", "&f%player%"), java.util.Map.of("player", player.getName())),
                    DimensionGuiBuilder.lore(cfg, base + "head-lore", java.util.Map.of("player", player.getName()))));
            slot++;
        }

        if (players.isEmpty()) {
            inventory.setItem(22, GuiItems.build(plugin, Material.BARRIER, null,
                    cfg.getString(base + "empty-message"), null));
        }

        // Only the bottom row is decorative here - the head grid above it is
        // meant to stay a clean roster, not framed like the other menus.
        inventory.setItem(49, GuiItems.build(plugin, Material.ARROW, "PLIST_BACK",
                cfg.getString(base + "back-button.name"), DimensionGuiBuilder.lore(cfg, base + "back-button.lore", java.util.Map.of())));
        Inventory bottom = Bukkit.createInventory(null, 9);
        GuiItems.frame(bottom, plugin, GuiItems.themeFor(dimension), 1);
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(45 + i) == null) {
                inventory.setItem(45 + i, bottom.getItem(i));
            }
        }

        return inventory;
    }
}
