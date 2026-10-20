package net.dimensiontoggle.gui;

import net.dimensiontoggle.PluginTestBase;
import net.dimensiontoggle.manager.MobManagementManager;
import net.dimensiontoggle.model.WorldScope;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The 3.2.0 editor additions: Overworld mob groups with pages, own cleanup
// intervals typed in the chat, the new frame.
class EditorFeaturesTest extends PluginTestBase {

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

    private int slotOf(String action) {
        for (int i = 0; i < top().getSize(); i++) {
            if (action.equals(GuiItems.readAction(plugin, top().getItem(i)))) {
                return i;
            }
        }
        throw new AssertionError("no " + action + " in menu");
    }

    private boolean has(String action) {
        for (int i = 0; i < top().getSize(); i++) {
            if (action.equals(GuiItems.readAction(plugin, top().getItem(i)))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("removal")
    private void click(String action, ClickType type) {
        admin.simulateInventoryClick(admin.getOpenInventory(), type, slotOf(action));
        server.getScheduler().performOneTick();
    }

    private void click(String action) {
        click(action, ClickType.LEFT);
    }

    private void chat(String text) {
        admin.chat(text);
        server.getScheduler().waitAsyncEventsFinished();
        server.getScheduler().performOneTick();
    }

    private List<String> messages() {
        List<String> lines = new ArrayList<>();
        for (Component c = admin.nextComponentMessage(); c != null; c = admin.nextComponentMessage()) {
            lines.add(PlainTextComponentSerializer.plainText().serialize(c));
        }
        return lines;
    }

    private void openOverworldGroup(String category) {
        click("MAIN_OPEN_OVERWORLD");
        click("OW_MOBMANAGEMENT");
        click("MOBCAT_" + category);
    }

    @Test
    void everyLivingOverworldMobIsManagedExactlyOnce() {
        Set<EntityType> seen = new HashSet<>();
        for (EntityType type : MobManagementManager.OVERWORLD_MOBS) {
            assertTrue(seen.add(type), type + " is listed twice");
            assertTrue(type.isAlive(), type + " is not a mob");
        }
        Set<EntityType> expected = EnumSet.of(EntityType.WARDEN, EntityType.VILLAGER, EntityType.IRON_GOLEM,
                EntityType.DOLPHIN, EntityType.ARMADILLO, EntityType.BREEZE, EntityType.HAPPY_GHAST,
                EntityType.WITHER, EntityType.ELDER_GUARDIAN, EntityType.COPPER_GOLEM);
        assertTrue(seen.containsAll(expected));
        assertFalse(seen.contains(EntityType.PLAYER));
        assertFalse(seen.contains(EntityType.BLAZE), "Nether-only mobs stay in the Nether list");
    }

    @Test
    void mobGroupsOpenAndAnimalsAreSplitIntoPages() {
        openOverworldGroup("ANIMALS");
        assertTrue(has("MOB_TOGGLE_COW"));
        assertFalse(has("MOB_PAGE_PREVIOUS"));
        assertTrue(has("MOB_PAGE_NEXT"), "29 animals don't fit on one page");

        click("MOB_PAGE_NEXT");
        assertTrue(has("MOB_TOGGLE_HAPPY_GHAST"));
        assertFalse(has("MOB_TOGGLE_COW"));
        assertTrue(has("MOB_PAGE_PREVIOUS"));

        click("MOB_BACK");
        assertTrue(has("MOBCAT_WATER"), "back leads to the groups");
        click("MOBCAT_BACK");
        assertTrue(has("OW_MOBMANAGEMENT"));
    }

    @Test
    void rightClickOnAMobAsksForAnIntervalInTheChat() {
        openOverworldGroup("MONSTERS");
        messages();
        click("MOB_TOGGLE_CREEPER", ClickType.RIGHT);

        assertEquals(InventoryType.CRAFTING, admin.getOpenInventory().getType(), "menu closes for typing");
        assertTrue(plugin.getGuiManager().getChatInput().isWaiting(admin));
        assertFalse(messages().isEmpty(), "the admin is told what to type");

        chat("1h30m");
        assertEquals(90, plugin.getMobManagementManager().getCleanupMinutes(WorldScope.OVERWORLD, EntityType.CREEPER));
        assertFalse(plugin.getGuiManager().getChatInput().isWaiting(admin));
        assertTrue(has("MOB_TOGGLE_CREEPER"), "the same mob list opens again");
        assertTrue(plugin.getMobManagementManager().isSpawnEnabled(WorldScope.OVERWORLD, EntityType.CREEPER),
                "right-click doesn't touch spawning");
    }

    @Test
    void invalidInputKeepsWaitingAndZeroSwitchesOff() {
        plugin.getMobManagementManager().setCleanupMinutes(WorldScope.OVERWORLD, EntityType.ZOMBIE, 10);
        openOverworldGroup("MONSTERS");
        click("MOB_TOGGLE_ZOMBIE", ClickType.RIGHT);

        chat("25h");
        assertTrue(plugin.getGuiManager().getChatInput().isWaiting(admin), "a typo doesn't end the input");
        assertEquals(10, plugin.getMobManagementManager().getCleanupMinutes(WorldScope.OVERWORLD, EntityType.ZOMBIE));

        chat("0");
        assertEquals(0, plugin.getMobManagementManager().getCleanupMinutes(WorldScope.OVERWORLD, EntityType.ZOMBIE));
    }

    @Test
    void cancelAndTimeoutChangeNothingAndReopenTheMenu() {
        openOverworldGroup("WATER");
        click("MOB_TOGGLE_SQUID", ClickType.RIGHT);
        chat("abbrechen");
        assertEquals(0, plugin.getMobManagementManager().getCleanupMinutes(WorldScope.OVERWORLD, EntityType.SQUID));
        assertTrue(has("MOB_TOGGLE_SQUID"));

        click("MOB_TOGGLE_SQUID", ClickType.RIGHT);
        server.getScheduler().performTicks(ChatInput.TIMEOUT_TICKS + 1);
        assertFalse(plugin.getGuiManager().getChatInput().isWaiting(admin));
        assertTrue(has("MOB_TOGGLE_SQUID"), "menu is back after the timeout");
    }

    @Test
    void itemCleanerIntervalIsSetByRightClick() {
        click("MAIN_OPEN_OVERWORLD");
        click("OW_ITEMCLEANER", ClickType.RIGHT);
        chat("45");

        var config = plugin.getConfigManager().getConfig();
        assertEquals(45, config.getInt("item-cleaner.interval-minutes"));
        assertTrue(config.getBoolean("item-cleaner.enabled"), "setting a time switches the cleaner on");
        assertTrue(plugin.getItemCleaner().getSecondsLeft() > 0);
        assertTrue(has("OW_ITEMCLEANER"), "panel opens again");

        click("OW_ITEMCLEANER", ClickType.RIGHT);
        chat("aus");
        assertFalse(config.getBoolean("item-cleaner.enabled"));
        assertEquals(45, config.getInt("item-cleaner.interval-minutes"), "off keeps the last interval");
    }

    @Test
    void chatAnswersNeverReachThePublicChat() {
        PlayerMock other = addPlayer("Other");
        click("MAIN_OPEN_OVERWORLD");
        click("OW_ITEMCLEANER", ClickType.RIGHT);
        other.nextComponentMessage();
        while (other.nextComponentMessage() != null) {
            // discard join chatter
        }
        chat("20m");
        assertEquals(null, other.nextComponentMessage());
    }

    @Test
    void parsingAcceptsMinutesAndDurationsWithinOneMinuteToOneDay() {
        assertEquals(15, ChatInput.parseMinutes("15"));
        assertEquals(15, ChatInput.parseMinutes("15m"));
        assertEquals(120, ChatInput.parseMinutes(" 2h "));
        assertEquals(90, ChatInput.parseMinutes("1h 30m"));
        assertEquals(1440, ChatInput.parseMinutes("24h"));
        assertEquals(0, ChatInput.parseMinutes("off"));
        assertEquals(ChatInput.CANCEL, ChatInput.parseMinutes("Cancel"));
        assertEquals(ChatInput.INVALID, ChatInput.parseMinutes("30s"));
        assertEquals(ChatInput.INVALID, ChatInput.parseMinutes("90s"));
        assertEquals(ChatInput.INVALID, ChatInput.parseMinutes("25h"));
        assertEquals(ChatInput.INVALID, ChatInput.parseMinutes("soon"));
    }

    @Test
    void togglesAnswerWithAnOnOffSound() {
        click("MAIN_OPEN_NETHER");
        click("DIM_TOGGLE");   // closes the Nether
        admin.assertSoundHeard(org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS);
        click("DIM_TOGGLE");   // opens it again
        admin.assertSoundHeard(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING);
        click("DIM_BACK");
        admin.assertSoundHeard(org.bukkit.Sound.UI_BUTTON_CLICK);
    }

    @Test
    void controlPanelIsMirroredAroundTheMiddleColumn() {
        click("MAIN_OPEN_NETHER");
        assertEquals(DimensionGuiBuilder.ROWS * 9, top().getSize());
        assertEquals(DimensionGuiBuilder.TOGGLE_SLOT, slotOf("DIM_TOGGLE"));
        assertEquals(12, slotOf("DIM_PLAYERLIST") - 2);
        // access | automation, the middle (22) stays free
        assertEquals(List.of(20, 21, 23, 24), List.of(slotOf("DIM_SOFTLOCK"), slotOf("DIM_LIMIT"),
                slotOf("DIM_MAINTENANCE"), slotOf("DIM_SCHEDULE")));
        assertEquals(List.of(29, 30, 32, 33), List.of(slotOf("DIM_KEEPINV"), slotOf("DIM_PVP_UNAVAILABLE"),
                slotOf("DIM_MOBSPAWN"), slotOf("DIM_SPAWNERS")));
        assertEquals(List.of(39, 41), List.of(slotOf("DIM_MOBMANAGEMENT"), slotOf("DIM_WORLDBORDER")));
        assertEquals(49, slotOf("DIM_BACK"));

        click("DIM_BACK");
        click("MAIN_OPEN_END");
        assertEquals(List.of(29, 30, 31, 32, 33), List.of(slotOf("DIM_KEEPINV"), slotOf("DIM_PVP_UNAVAILABLE"),
                slotOf("DIM_MOBSPAWN"), slotOf("DIM_ELYTRA"), slotOf("DIM_GATEWAY")), "End: five rules in one block");
    }

    @Test
    void switchedOffFeaturesKeepTheirOwnIcon() {
        click("MAIN_OPEN_END");   // keep inventory off, gateways blocked, PvP unavailable
        for (int i = 0; i < top().getSize(); i++) {
            assertFalse(top().getItem(i) != null && top().getItem(i).getType() == Material.BARRIER,
                    "no barrier icons in the control panel (slot " + i + ")");
        }
        assertEquals(Material.TOTEM_OF_UNDYING, top().getItem(slotOf("DIM_KEEPINV")).getType());
        assertEquals(Material.ENDER_EYE, top().getItem(slotOf("DIM_GATEWAY")).getType());
    }

    @Test
    void axisSlotsMirrorAroundTheMiddle() {
        assertEquals(List.of(13), toList(GuiItems.axisSlots(1, 1)));
        assertEquals(List.of(12, 14), toList(GuiItems.axisSlots(1, 2)));
        assertEquals(List.of(12, 13, 14), toList(GuiItems.axisSlots(1, 3)));
        assertEquals(List.of(11, 12, 14, 15), toList(GuiItems.axisSlots(1, 4)));
        assertEquals(List.of(11, 12, 13, 14, 15), toList(GuiItems.axisSlots(1, 5)));
        assertEquals(List.of(10, 11, 12, 14, 15, 16), toList(GuiItems.axisSlots(1, 6)));
    }

    private static List<Integer> toList(int[] slots) {
        return java.util.Arrays.stream(slots).boxed().toList();
    }

    @Test
    void helpLinesComeWithoutThePrefix() {
        messages();
        admin.performCommand("dt help");
        List<String> lines = messages();
        assertTrue(lines.size() > 5);
        assertTrue(lines.get(0).contains("DimensionToggle"), lines.get(0));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith(" /dt editor")), String.valueOf(lines));
        assertTrue(lines.stream().noneMatch(l -> l.contains("» /dt")), "no prefix in front of help lines");
        assertTrue(lines.stream().anyMatch(l -> l.contains("/dt nether <on|off>")), "argument hints stay readable");
    }

    @Test
    void menusAreFramedInTheirThemeColor() {
        assertEquals(Material.MAGENTA_STAINED_GLASS_PANE, top().getItem(0).getType(), "main: accent corner");
        assertEquals(Material.PURPLE_STAINED_GLASS_PANE, top().getItem(1).getType(), "main: border");
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, top().getItem(10).getType(), "dark inside");
        click("MAIN_OPEN_NETHER");
        assertEquals(Material.ORANGE_STAINED_GLASS_PANE, top().getItem(0).getType());
        assertEquals(Material.RED_STAINED_GLASS_PANE, top().getItem(1).getType());
    }
}
