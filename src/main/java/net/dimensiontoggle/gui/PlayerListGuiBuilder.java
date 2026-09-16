package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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
        String headLore = cfg.getString(base + "head-lore");

        int slot = 0;
        for (Player player : players) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, GuiItems.buildPlayerHead(plugin, player, "PLIST_TP_" + player.getUniqueId(),
                    "&f" + player.getName(), List.of(headLore)));
            slot++;
        }

        if (players.isEmpty()) {
            inventory.setItem(22, GuiItems.build(plugin, Material.BARRIER, null,
                    cfg.getString(base + "empty-message"), null));
        }

        // Only the bottom row is decorative here - the head grid above it is
        // meant to stay a clean roster, not framed like the other menus.
        ItemStack filler = GuiItems.build(plugin, GuiItems.fillerFor(dimension), null, " ", null);
        ItemStack border = GuiItems.build(plugin, GuiItems.borderFor(dimension), null, " ", null);
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, (i == 45 || i == 53) ? border : filler);
        }
        inventory.setItem(49, GuiItems.build(plugin, Material.ARROW, "PLIST_BACK",
                cfg.getString(base + "back-button.name"), cfg.getStringList(base + "back-button.lore")));

        return inventory;
    }
}
