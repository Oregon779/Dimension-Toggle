package net.dimensiontoggle;

import org.bukkit.Material;
import org.mockbukkit.mockbukkit.world.WorldMock;

// MockBukkit's WorldMock lacks a few counters the performance monitor reads.
public class TestWorldMock extends WorldMock {

    public TestWorldMock(String name) {
        super(Material.GRASS_BLOCK, 4);
        setName(name);
    }

    @Override
    public int getViewDistance() {
        return 10;
    }

    @Override
    public int getSimulationDistance() {
        return 10;
    }

    // ChunkMock can't list block entities either, so there's nothing to count.
    @Override
    public int getTileEntityCount() {
        return 0;
    }
}
