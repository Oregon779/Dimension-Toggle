package net.dimensiontoggle.listener;

import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.dimensiontoggle.PluginTestBase;
import net.dimensiontoggle.model.ToggleDimension;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalListenerTest extends PluginTestBase {

    private PlayerMock player;

    @BeforeEach
    void addPlayer() {
        player = server.addPlayer("Steve");
        player.setOp(false);
        drainMessages(player);
    }

    private PlayerPortalEvent portal(PlayerMock who, World from, World to, TeleportCause cause) {
        PlayerPortalEvent event = new PlayerPortalEvent(who, from.getSpawnLocation(), to.getSpawnLocation(), cause);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private PlayerPortalEvent intoNether(PlayerMock who) {
        return portal(who, overworld, nether, TeleportCause.NETHER_PORTAL);
    }

    private static void drainMessages(PlayerMock who) {
        while (who.nextComponentMessage() != null) {
            // discard join/setup chatter
        }
    }

    private static String nextPlain(PlayerMock who) {
        Component message = who.nextComponentMessage();
        return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
    }

    @Test
    void enabledDimensionIsReachable() {
        assertFalse(intoNether(player).isCancelled());
        assertNull(player.nextComponentMessage());
    }

    @Test
    void disabledDimensionBlocksEntryWithMessage() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);

        assertTrue(intoNether(player).isCancelled());
        assertNotNull(player.nextComponentMessage());
    }

    @Test
    void leavingADisabledDimensionIsAlwaysAllowed() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);

        assertFalse(portal(player, nether, overworld, TeleportCause.NETHER_PORTAL).isCancelled());
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.END, false, null);
        assertFalse(portal(player, end, overworld, TeleportCause.END_PORTAL).isCancelled());
    }

    @Test
    void bypassPermissionIgnoresDisabledState() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.END, false, null);
        player.addAttachment(plugin, "dimensiontoggle.bypass", true);

        assertFalse(portal(player, overworld, end, TeleportCause.END_PORTAL).isCancelled());
    }

    @Test
    void softLockBlocksEntry() {
        plugin.getDimensionManager().setLocked(ToggleDimension.NETHER, true, null);

        assertTrue(intoNether(player).isCancelled());
    }

    @Test
    void playerLimitBlocksOnlyWhenFullAndRespectsPerDimensionBypass() {
        plugin.getConfigManager().getConfig().set("limits.nether.enabled", true);
        plugin.getConfigManager().getConfig().set("limits.nether.max-players", 1);

        assertFalse(intoNether(player).isCancelled());

        PlayerMock inside = server.addPlayer("Alex");
        inside.teleport(new Location(nether, 0, 64, 0));
        PlayerPortalEvent blocked = intoNether(player);
        assertTrue(blocked.isCancelled());
        String message = null;
        for (String next = nextPlain(player); next != null; next = nextPlain(player)) {
            message = next;
        }
        assertNotNull(message);
        assertTrue(message.contains("1"), "limit placeholder should be filled: " + message);

        player.addAttachment(plugin, "dimensiontoggle.bypass.limit.nether", true);
        assertFalse(intoNether(player).isCancelled());
        // The Nether bypass must not open the End's limit.
        plugin.getConfigManager().getConfig().set("limits.end.enabled", true);
        plugin.getConfigManager().getConfig().set("limits.end.max-players", 0);
        assertTrue(portal(player, overworld, end, TeleportCause.END_PORTAL).isCancelled());
    }

    @Test
    void alreadyCancelledEventsAreLeftAlone() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);
        PlayerPortalEvent event = new PlayerPortalEvent(player, overworld.getSpawnLocation(),
                nether.getSpawnLocation(), TeleportCause.NETHER_PORTAL);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);

        assertNull(player.nextComponentMessage(), "no message for a trip another plugin already blocked");
    }

    @Test
    void standingInABlockedPortalDoesNotSpamChat() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);

        for (int i = 0; i < 10; i++) {
            assertTrue(intoNether(player).isCancelled(), "every attempt must stay blocked");
        }
        assertNotNull(player.nextComponentMessage());
        assertNull(player.nextComponentMessage());
    }

    @Test
    void endGatewayIsBlockedWithItsOwnMessageEvenIfEndIsOpen() {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, end.getSpawnLocation(),
                new Location(end, 1000, 64, 0), TeleportCause.END_GATEWAY);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        String message = nextPlain(player);
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("gateway"), message);
    }

    @Test
    void endGatewayBlockingCanBeTurnedOff() {
        plugin.getConfigManager().getConfig().set("block-end-gateways", false);
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, end.getSpawnLocation(),
                new Location(end, 1000, 64, 0), TeleportCause.END_GATEWAY);
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }
}
