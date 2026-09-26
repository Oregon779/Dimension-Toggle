package net.dimensiontoggle;

import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.inventory.InventoryMock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.InventoryHolder;

// MockBukkit 3.x throws UnimplementedOperationException for the Adventure
// title overload the plugin (correctly) uses on real Paper servers. Fill in
// that one gap for tests instead of bending production code around it.
public class TestServerMock extends ServerMock {

    @Override
    public InventoryMock createInventory(InventoryHolder owner, int size, Component title) {
        return createInventory(owner, size, LegacyComponentSerializer.legacySection().serialize(title));
    }

    // Read by the dimension dashboard; unimplemented in MockBukkit 3.x.
    @Override
    public double[] getTPS() {
        return new double[]{20.0, 20.0, 20.0};
    }
}
