package net.dimensiontoggle;

import org.mockbukkit.mockbukkit.ServerMock;

// Fills gaps in MockBukkit instead of bending production code around them.
public class TestServerMock extends ServerMock {

    // Read by the dimension dashboard; fixed values keep tests deterministic.
    @Override
    public double[] getTPS() {
        return new double[]{20.0, 20.0, 20.0};
    }
}
