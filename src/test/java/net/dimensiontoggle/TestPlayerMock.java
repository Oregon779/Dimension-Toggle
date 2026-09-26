package net.dimensiontoggle;

import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.concurrent.CompletableFuture;

// MockBukkit 3.x's teleportAsync is a stub that never moves the player. The
// plugin uses it (correctly) on Paper, so map it to a plain teleport here.
public class TestPlayerMock extends PlayerMock {

    public TestPlayerMock(ServerMock server, String name) {
        super(server, name);
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location, TeleportCause cause, TeleportFlag... flags) {
        return CompletableFuture.completedFuture(teleport(location, cause));
    }
}
