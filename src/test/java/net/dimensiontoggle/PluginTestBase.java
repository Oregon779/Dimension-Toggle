package net.dimensiontoggle;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class PluginTestBase {

    protected ServerMock server;
    protected DimensionToggle plugin;
    protected WorldMock overworld;
    protected WorldMock nether;
    protected WorldMock end;

    @BeforeEach
    void setUpServer() {
        server = MockBukkit.mock(new TestServerMock());
        // Order matters: the plugin treats Bukkit.getWorlds().get(0) as the
        // main world when sending players back to spawn.
        overworld = world("world", World.Environment.NORMAL);
        nether = world("world_nether", World.Environment.NETHER);
        end = world("world_the_end", World.Environment.THE_END);

        plugin = MockBukkit.load(DimensionToggle.class);
        // Never let a test run into the real Modrinth HTTP call.
        plugin.getUpdateChecker().stop();
    }

    @AfterEach
    void tearDownServer() {
        MockBukkit.unmock();
    }

    private WorldMock world(String name, World.Environment environment) {
        WorldMock world = server.addSimpleWorld(name);
        world.setEnvironment(environment);
        return world;
    }
}
