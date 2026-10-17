package net.dimensiontoggle.performance;

import net.dimensiontoggle.PluginTestBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverworldUiTest extends PluginTestBase {

    private PlayerMock admin;

    @BeforeEach
    void openEditor() {
        admin = addPlayer("Admin");
        admin.setOp(true);
        admin.performCommand("dt editor");
    }

    private Inventory top() {
        return admin.getOpenInventory().getTopInventory();
    }

    @SuppressWarnings("removal")
    private void click(int slot) {
        admin.simulateInventoryClick(admin.getOpenInventory(), ClickType.LEFT, slot);
        server.getScheduler().performOneTick();
    }

    private int slotOf(Material material) {
        for (int i = 0; i < top().getSize(); i++) {
            if (top().getItem(i) != null && top().getItem(i).getType() == material) {
                return i;
            }
        }
        throw new AssertionError("no " + material + " in menu");
    }

    @Test
    void mainMenuLeadsToTheOverworldPanel() {
        assertEquals(Material.GRASS_BLOCK, top().getItem(13).getType());
        click(13);
        assertEquals(54, top().getSize());
        slotOf(Material.FILLED_MAP); // lag map button is there
    }

    @Test
    void togglingAFeatureInThePanelSavesIt() {
        click(13);
        click(slotOf(Material.WHEAT));   // farm limiter, currently off
        assertTrue(plugin.getConfigManager().getConfig().getBoolean("farm-limiter.enabled"));
        slotOf(Material.HAY_BLOCK);      // icon switched to "on"
    }

    @Test
    void overworldMobSpawningToggleUsesTheGamerule() {
        click(13);
        click(slotOf(Material.SPAWNER));
        assertEquals(Boolean.FALSE, overworld.getGameRuleValue(GameRules.SPAWN_MOBS));
        assertTrue(nether.getGameRuleValue(GameRules.SPAWN_MOBS) != Boolean.FALSE, "only the Overworld");
    }

    @Test
    void overworldMobListBlocksSpawningPerType() {
        click(13);
        click(slotOf(Material.ZOMBIE_HEAD));
        click(slotOf(Material.ZOMBIE_HEAD));   // first mob of the Overworld list
        assertFalse(plugin.getMobManagementManager().isSpawnEnabled(net.dimensiontoggle.model.WorldScope.OVERWORLD,
                org.bukkit.entity.EntityType.ZOMBIE));
        assertTrue(plugin.getMobManagementManager().isSpawnEnabled(net.dimensiontoggle.model.WorldScope.NETHER,
                org.bukkit.entity.EntityType.ZOMBIFIED_PIGLIN));
    }

    @Test
    void consoleCanReadTheRedstoneAndChunkLoaderLists() {
        var console = server.getConsoleSender();
        server.dispatchCommand(console, "dt redstone chat");
        server.dispatchCommand(console, "dt chunkloaders chat");
        List<String> lines = new ArrayList<>();
        for (Component c = ((org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock) console).nextComponentMessage();
             c != null; c = ((org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock) console).nextComponentMessage()) {
            lines.add(PlainTextComponentSerializer.plainText().serialize(c));
        }
        assertTrue(lines.size() >= 2, String.valueOf(lines));
    }

    @Test
    void lagToolsNeedTheirPermission() {
        PlayerMock regular = server.addPlayer("Regular");
        regular.setOp(false);
        regular.performCommand("dt lagmap chat");
        regular.performCommand("lagmap chat");
        String denied = PlainTextComponentSerializer.plainText().serialize(plugin.getMessageManager()
                .parse(plugin.getMessageManager().getPrefix() + plugin.getMessageManager().get("no-permission")));
        assertEquals(denied, PlainTextComponentSerializer.plainText().serialize(regular.nextComponentMessage()));
    }

    @Test
    void tpchunkTeleportsIntoTheChunk() {
        admin.closeInventory();
        admin.performCommand("dt tpchunk " + overworld.getName() + " 3 -2");
        server.getScheduler().performOneTick();
        assertEquals(3, admin.getLocation().getBlockX() >> 4);
        assertEquals(-2, admin.getLocation().getBlockZ() >> 4);
    }

    @Test
    void webCommandSaysWhenTheWebsiteIsOff() {
        while (admin.nextComponentMessage() != null) {
            // discard
        }
        admin.performCommand("dt web");
        String expected = PlainTextComponentSerializer.plainText().serialize(plugin.getMessageManager()
                .parse(plugin.getMessageManager().getPrefix() + plugin.getMessageManager().get("web-disabled")));
        assertEquals(expected, PlainTextComponentSerializer.plainText().serialize(admin.nextComponentMessage()));
    }
}
