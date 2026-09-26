package net.dimensiontoggle;

import be.seeseemelk.mockbukkit.MockBukkit;
import net.dimensiontoggle.model.ToggleDimension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLifecycleTest extends PluginTestBase {

    @Test
    void enablesWithBothDimensionsOpenByDefault() {
        assertTrue(plugin.isEnabled());
        assertTrue(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
        assertTrue(plugin.getDimensionManager().isEnabled(ToggleDimension.END));
    }

    @Test
    void disableAndReEnableKeepsPersistedState() {
        plugin.getDimensionManager().setEnabled(ToggleDimension.NETHER, false);

        server.getPluginManager().disablePlugin(plugin);
        assertFalse(plugin.isEnabled());

        DimensionToggle reloaded = MockBukkit.load(DimensionToggle.class);
        reloaded.getUpdateChecker().stop();
        assertFalse(reloaded.getDimensionManager().isEnabled(ToggleDimension.NETHER));
        assertTrue(reloaded.getDimensionManager().isEnabled(ToggleDimension.END));
    }
}
