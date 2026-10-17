package net.dimensiontoggle.performance;

import net.dimensiontoggle.PluginTestBase;
import net.dimensiontoggle.hook.PlaceholderHook;
import net.dimensiontoggle.model.ToggleDimension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaceholderTest extends PluginTestBase {

    @Test
    void placeholdersReflectTheLiveState() {
        PlaceholderHook hook = new PlaceholderHook(plugin);
        assertEquals("true", hook.onRequest(null, "nether_enabled"));
        assertEquals("0", hook.onRequest(null, "end_players"));
        assertEquals("-", hook.onRequest(null, "nether_limit"));
        assertEquals("false", hook.onRequest(null, "lockdown"));

        plugin.getDimensionManager().setEnabledSilently(ToggleDimension.NETHER, false, null);
        plugin.getConfigManager().getConfig().set("limits.end.enabled", true);
        plugin.getConfigManager().getConfig().set("limits.end.max-players", 7);

        assertEquals("false", hook.onRequest(null, "nether_enabled"));
        assertEquals(plugin.getMessageManager().get("placeholder-disabled"), hook.onRequest(null, "nether_status"));
        assertEquals("7", hook.onRequest(null, "end_limit"));
        assertNull(hook.onRequest(null, "nonsense"));
        assertNull(hook.onRequest(null, "moon_enabled"));
    }
}
