package net.dimensiontoggle;

import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceBehaviourTest extends PluginTestBase {

    private List<PlayerMock> playersIn(World world, int count) {
        List<PlayerMock> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PlayerMock player = addPlayer("P" + world.getName().hashCode() + "_" + i);
            player.teleport(new Location(world, i, 64, 0));
            players.add(player);
        }
        return players;
    }

    private static long stillIn(List<PlayerMock> players, World world) {
        return players.stream().filter(p -> p.getWorld().equals(world)).count();
    }

    @Test
    void smallGroupsAreRemovedInstantly() {
        List<PlayerMock> players = playersIn(nether, 5);

        plugin.getDimensionManager().removePlayersFromDimension(ToggleDimension.NETHER, "");

        assertEquals(0, stillIn(players, nether));
    }

    @Test
    void largeGroupsAreSpreadOverTicksButEveryoneLeaves() {
        List<PlayerMock> players = playersIn(nether, 55);

        plugin.getDimensionManager().removePlayersFromDimension(ToggleDimension.NETHER, null);
        long afterFirstTick = stillIn(players, nether);
        assertTrue(afterFirstTick > 0 && afterFirstTick < 55, "first batch leaves at once, rest later: " + afterFirstTick);

        server.getScheduler().performTicks(5);
        assertEquals(0, stillIn(players, nether));
        players.forEach(p -> assertEquals(overworld, p.getWorld()));
    }

    @Test
    void queuedPlayersWhoLeftOnTheirOwnAreNotTeleportedAgain() {
        List<PlayerMock> players = playersIn(end, 30);
        plugin.getDimensionManager().removePlayersFromDimension(ToggleDimension.END, null);

        // Someone still queued walks through a portal into the Nether first.
        PlayerMock mover = players.stream().filter(p -> p.getWorld().equals(end)).findFirst().orElseThrow();
        mover.teleport(new Location(nether, 0, 64, 0));
        PlayerMock quitter = players.stream().filter(p -> p.getWorld().equals(end)).findFirst().orElseThrow();
        quitter.disconnect();

        server.getScheduler().performTicks(3);
        assertEquals(nether, mover.getWorld());
        assertEquals(0, players.stream().filter(p -> p != quitter && p.getWorld().equals(end)).count());
    }

    @Test
    void lockdownClearsAFullDimension() {
        List<PlayerMock> players = playersIn(nether, 45);

        plugin.getDimensionManager().toggleLockdownWithBroadcast(server.getConsoleSender());
        server.getScheduler().performTicks(5);

        assertEquals(0, stillIn(players, nether));
        assertTrue(plugin.getDimensionManager().isLockdownActive());
    }

    @Test
    void identicalTextIsParsedOnceAndShared() {
        var messages = plugin.getMessageManager();
        String raw = "<gradient:#A78BFA:#7C3AED><bold>DT Toggle</bold></gradient> &7| &fStatus";

        assertSame(messages.parse(raw), messages.parse(raw));
        assertNotEquals(messages.parse(raw), messages.parse(raw + "!"));

        messages.clearCache();
        assertEquals(messages.parse(raw), messages.parse(raw), "same result after clearing");
    }
}
