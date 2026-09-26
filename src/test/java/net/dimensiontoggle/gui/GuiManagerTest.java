package net.dimensiontoggle.gui;

import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.dimensiontoggle.PluginTestBase;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiManagerTest extends PluginTestBase {

    // Slots from MainGuiBuilder / DimensionGuiBuilder.
    private static final int MAIN_NETHER_SLOT = 11;
    private static final int DIMENSION_TOGGLE_SLOT = 13;

    private PlayerMock admin;

    @BeforeEach
    void openEditor() {
        admin = server.addPlayer("Admin");
        admin.setOp(true);
        admin.performCommand("dt editor");
    }

    private Inventory top() {
        return admin.getOpenInventory().getTopInventory();
    }

    @Test
    void editorOpensTheMainMenu() {
        assertEquals(36, top().getSize());
        assertEquals(Material.NETHERRACK, top().getItem(MAIN_NETHER_SLOT).getType());
    }

    @Test
    void nonAdminCannotOpenTheEditor() {
        PlayerMock regular = server.addPlayer("Regular");
        regular.performCommand("dt editor");
        assertEquals(InventoryType.CRAFTING, regular.getOpenInventory().getType());
    }

    @Test
    void navigationHappensOnTheNextTickNotInsideTheClickEvent() {
        Inventory main = top();
        InventoryClickEvent click = admin.simulateInventoryClick(admin.getOpenInventory(), ClickType.LEFT, MAIN_NETHER_SLOT);

        assertTrue(click.isCancelled());
        assertSame(main, top(), "must not switch inventories inside the click event");

        server.getScheduler().performOneTick();
        assertNotSame(main, top());
        assertEquals(54, top().getSize());
    }

    @Test
    void toggleAppliesImmediatelyAndRefreshesInPlace() {
        admin.simulateInventoryClick(admin.getOpenInventory(), ClickType.LEFT, MAIN_NETHER_SLOT);
        server.getScheduler().performOneTick();
        Inventory panel = top();
        assertEquals(Material.LIME_DYE, panel.getItem(DIMENSION_TOGGLE_SLOT).getType());

        admin.simulateInventoryClick(admin.getOpenInventory(), ClickType.LEFT, DIMENSION_TOGGLE_SLOT);
        assertFalse(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));

        server.getScheduler().performOneTick();
        assertSame(panel, top(), "a toggle should update the open menu, not reopen it");
        assertEquals(Material.GRAY_DYE, panel.getItem(DIMENSION_TOGGLE_SLOT).getType());
    }

    @Test
    void clicksInThePlayersOwnInventoryAreCancelledButTriggerNothing() {
        // Put a copy of a GUI button into the player's own inventory - e.g. a
        // leftover from before this fix - and click it while a menu is open.
        ItemStack copy = top().getItem(MAIN_NETHER_SLOT).clone();
        admin.getInventory().setItem(0, copy);
        int playerSlotRaw = top().getSize() + 27; // hotbar slot 0 in the bottom inventory

        Inventory main = top();
        InventoryClickEvent click = admin.simulateInventoryClick(admin.getOpenInventory(), ClickType.LEFT, playerSlotRaw);
        server.getScheduler().performOneTick();

        assertTrue(click.isCancelled());
        assertSame(main, top());
    }

    @Test
    void draggingIntoAMenuIsCancelled() {
        InventoryDragEvent drag = new InventoryDragEvent(admin.getOpenInventory(), null,
                new ItemStack(Material.DIRT, 2), false, Map.of(0, new ItemStack(Material.DIRT)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
    }

    @Test
    void disablingThePluginClosesOpenMenus() {
        Inventory main = top();
        server.getPluginManager().disablePlugin(plugin);
        assertNotSame(main, top());
        assertEquals(InventoryType.CRAFTING, admin.getOpenInventory().getType());
    }
}
