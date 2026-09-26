package net.dimensiontoggle;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicFixesTest extends PluginTestBase {

    @Test
    void pvpToggleSurvivesARestart() {
        assertTrue(nether.getPVP());
        assertFalse(plugin.getPvpIntegrationManager().toggle(ToggleDimension.NETHER));
        assertFalse(nether.getPVP());

        server.getPluginManager().disablePlugin(plugin);
        // World#setPVP isn't saved with the world - simulate the restart
        // resetting it to the server default.
        nether.setPVP(true);
        DimensionToggle reloaded = MockBukkit.load(DimensionToggle.class);
        reloaded.getUpdateChecker().stop();

        assertFalse(nether.getPVP());
        assertTrue(end.getPVP(), "untouched dimensions keep the server's own setting");
    }

    @Test
    void mobCleanupSparesMountsThatCarryAPlayer() {
        Location location = new Location(nether, 0, 64, 0);
        Entity ridden = nether.spawnEntity(location, EntityType.BLAZE);
        Entity loose = nether.spawnEntity(location, EntityType.BLAZE);
        PlayerMock rider = server.addPlayer("Rider");
        rider.teleport(location);
        ridden.addPassenger(rider);

        // 0 -> 5 minutes
        assertEquals(5, plugin.getMobManagementManager().cycleCleanupMinutes(ToggleDimension.NETHER, EntityType.BLAZE));
        server.getScheduler().performTicks(20L * 60 * 5);

        assertTrue(loose.isDead() || !loose.isValid(), "unridden blaze should be cleaned up");
        assertFalse(ridden.isDead(), "ridden mount must not be removed from under its rider");
    }

    @Test
    void softLockDimensionScopeNotifiesOnlyPlayersInside() {
        plugin.getConfigManager().getConfig().set("softlock.broadcast-scope", "dimension");
        PlayerMock inside = server.addPlayer("Inside");
        inside.teleport(new Location(nether, 0, 64, 0));
        PlayerMock outside = server.addPlayer("Outside");
        drain(inside);
        drain(outside);

        plugin.getDimensionManager().toggleSoftLockWithBroadcast(ToggleDimension.NETHER, server.getConsoleSender());

        assertNotNull(inside.nextComponentMessage());
        assertNull(outside.nextComponentMessage());
    }

    @Test
    void disablingThePluginStopsMaintenanceQuietly() {
        PlayerMock player = server.addPlayer("Watcher");
        plugin.getMaintenanceManager().start(ToggleDimension.END, 300, null);
        assertTrue(plugin.getMaintenanceManager().isPending(ToggleDimension.END));
        drain(player);

        server.getPluginManager().disablePlugin(plugin);

        assertFalse(plugin.getMaintenanceManager().isPending(ToggleDimension.END));
        assertNull(player.nextComponentMessage(), "no 'maintenance cancelled' broadcast on shutdown");
        assertTrue(plugin.getDimensionManager().isEnabled(ToggleDimension.END));
    }

    @Test
    void maintenanceStillExecutesAfterTheInitiatorLoggedOut() {
        PlayerMock admin = server.addPlayer("Admin");
        plugin.getMaintenanceManager().start(ToggleDimension.NETHER, 3, admin);
        admin.disconnect();

        server.getScheduler().performTicks(20L * 4);

        assertFalse(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
        assertFalse(plugin.getMaintenanceManager().isPending(ToggleDimension.NETHER));
    }

    private static void drain(PlayerMock player) {
        while (player.nextComponentMessage() != null) {
            // discard
        }
    }
}
