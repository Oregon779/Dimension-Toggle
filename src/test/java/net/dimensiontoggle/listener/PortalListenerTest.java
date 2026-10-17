package net.dimensiontoggle.listener;

import com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import net.dimensiontoggle.PluginTestBase;
import net.dimensiontoggle.model.ToggleDimension;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.event.entity.EntityPortalEvent;
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

    private EntityPortalEvent ridePortal(World from, World to, PlayerMock... riders) {
        Pig mount = (Pig) from.spawnEntity(from.getSpawnLocation(), EntityType.PIG);
        for (PlayerMock rider : riders) {
            rider.teleport(from.getSpawnLocation());
            mount.addPassenger(rider);
        }
        EntityPortalEvent event = new EntityPortalEvent(mount, from.getSpawnLocation(), to.getSpawnLocation());
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void ridingIntoADisabledDimensionIsBlocked() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);

        assertTrue(ridePortal(overworld, nether, player).isCancelled(), "a mount must not carry players past the lock");
        assertNotNull(player.nextComponentMessage());
    }

    @Test
    void ridingRespectsSoftLockLimitAndBypass() {
        plugin.getDimensionManager().setLocked(ToggleDimension.END, true, null);
        assertTrue(ridePortal(overworld, end, player).isCancelled());

        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, "dimensiontoggle.bypass", true);
        assertFalse(ridePortal(overworld, end, staff).isCancelled());
    }

    @Test
    void oneRiderWithoutAccessStopsTheWholeVehicle() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, "dimensiontoggle.bypass", true);

        assertTrue(ridePortal(overworld, nether, staff, player).isCancelled());
    }

    @Test
    void ridersCountTowardsThePlayerLimitTogether() {
        plugin.getConfigManager().getConfig().set("limits.nether.enabled", true);
        plugin.getConfigManager().getConfig().set("limits.nether.max-players", 1);
        PlayerMock friend = server.addPlayer("Friend");
        friend.setOp(false);

        assertTrue(ridePortal(overworld, nether, player, friend).isCancelled(), "two riders don't fit a limit of one");
    }

    @Test
    void ridingOutOfADisabledDimensionIsAllowed() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);

        assertFalse(ridePortal(nether, overworld, player).isCancelled());
    }

    @Test
    void emptyVehiclesAndMobsAreNotOurBusiness() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);
        Pig pig = (Pig) overworld.spawnEntity(overworld.getSpawnLocation(), EntityType.PIG);
        EntityPortalEvent event = new EntityPortalEvent(pig, overworld.getSpawnLocation(), nether.getSpawnLocation());
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void ridingThroughABlockedEndGatewayIsBlocked() {
        assertTrue(ridePortal(end, end, player).isCancelled());
        plugin.getConfigManager().getConfig().set("block-end-gateways", false);
        assertFalse(ridePortal(end, end, player).isCancelled());
    }

    private EnderPearl pearl(World in, PlayerMock thrower) {
        EnderPearl pearl = in.spawn(in.getSpawnLocation(), EnderPearl.class);
        pearl.setShooter(thrower);
        return pearl;
    }

    private EntityPortalEvent pearlThroughPortal(World from, World to, PlayerMock thrower) {
        EntityPortalEvent event = new EntityPortalEvent(pearl(from, thrower), from.getSpawnLocation(),
                to.getSpawnLocation());
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void enderPearlIsStoppedAtADisabledPortal() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);

        assertTrue(pearlThroughPortal(overworld, nether, player).isCancelled(), "the pearl must not pull its thrower in");
        assertNotNull(player.nextComponentMessage());
        assertFalse(pearlThroughPortal(nether, overworld, player).isCancelled(), "leaving is always allowed");
    }

    @Test
    void enderPearlFollowsTheSameRulesAsWalkingThrough() {
        plugin.getDimensionManager().setLocked(ToggleDimension.END, true, null);
        assertTrue(pearlThroughPortal(overworld, end, player).isCancelled());

        plugin.getDimensionManager().setLocked(ToggleDimension.END, false, null);
        plugin.getConfigManager().getConfig().set("limits.end.enabled", true);
        plugin.getConfigManager().getConfig().set("limits.end.max-players", 0);
        assertTrue(pearlThroughPortal(overworld, end, player).isCancelled());

        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, "dimensiontoggle.bypass", true);
        assertFalse(pearlThroughPortal(overworld, end, staff).isCancelled());
    }

    @Test
    void pearlsWithoutAPlayerThrowerAreNotOurBusiness() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);
        EntityPortalEvent event = new EntityPortalEvent(overworld.spawn(overworld.getSpawnLocation(), EnderPearl.class),
                overworld.getSpawnLocation(), nether.getSpawnLocation());
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }

    private PlayerTeleportEvent pearlLanding(World from, World to) {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from.getSpawnLocation(), to.getSpawnLocation(),
                TeleportCause.ENDER_PEARL);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void aPearlThatGotThroughStillDoesNotTeleportIntoAClosedDimension() {
        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);

        assertTrue(pearlLanding(overworld, nether).isCancelled());
        assertNotNull(player.nextComponentMessage());
        assertFalse(pearlLanding(nether, overworld).isCancelled(), "leaving is always allowed");
        assertFalse(pearlLanding(overworld, overworld).isCancelled(), "a normal pearl throw");
    }

    @Test
    void enderPearlThroughABlockedEndGatewayIsStopped() {
        EntityTeleportEndGatewayEvent event = new EntityTeleportEndGatewayEvent(pearl(end, player),
                end.getSpawnLocation(), end.getSpawnLocation().add(1000, 0, 0), null);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());

        plugin.getConfigManager().getConfig().set("block-end-gateways", false);
        EntityTeleportEndGatewayEvent allowed = new EntityTeleportEndGatewayEvent(pearl(end, player),
                end.getSpawnLocation(), end.getSpawnLocation().add(1000, 0, 0), null);
        server.getPluginManager().callEvent(allowed);
        assertFalse(allowed.isCancelled());
    }
}
