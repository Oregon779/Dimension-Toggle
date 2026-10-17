package net.dimensiontoggle;

import org.mockbukkit.mockbukkit.ServerMock;

// Fills gaps in MockBukkit instead of bending production code around them.
public class TestServerMock extends ServerMock {

    // MockBukkit has no real tick loop; tests set the "measured" load here.
    private double averageTickTime = 10.0;

    public void setAverageTickTime(double millis) {
        this.averageTickTime = millis;
    }

    // Read by the dimension dashboard; fixed values keep tests deterministic.
    @Override
    public double[] getTPS() {
        return new double[]{20.0, 20.0, 20.0};
    }

    @Override
    public double getAverageTickTime() {
        return averageTickTime;
    }

    @Override
    public long[] getTickTimes() {
        long[] times = new long[100];
        java.util.Arrays.fill(times, (long) (averageTickTime * 1_000_000));
        return times;
    }
}
