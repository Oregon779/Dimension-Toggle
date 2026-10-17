package net.dimensiontoggle.performance;

import net.dimensiontoggle.PluginTestBase;
import net.dimensiontoggle.hook.PlaceholderHook;
import net.dimensiontoggle.model.ToggleDimension;
import net.dimensiontoggle.performance.web.PerformanceWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAndPlaceholderTest extends PluginTestBase {

    private final HttpClient http = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();

    @AfterEach
    void stopWeb() {
        plugin.getPerformanceWebServer().stop();
    }

    private PerformanceWebServer startWeb(boolean requireToken) {
        plugin.getConfigManager().getConfig().set("web.enabled", true);
        plugin.getConfigManager().getConfig().set("web.port", 0);   // any free port
        plugin.getConfigManager().getConfig().set("web.bind-address", "127.0.0.1");
        plugin.getConfigManager().getConfig().set("web.require-token", requireToken);
        PerformanceWebServer web = plugin.getPerformanceWebServer();
        web.start();
        assertTrue(web.isRunning());
        return web;
    }

    private HttpResponse<String> get(PerformanceWebServer web, String pathAndQuery) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + web.getPort() + pathAndQuery)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void websiteServesStatsOnlyWithTheToken() throws Exception {
        PerformanceWebServer web = startWeb(true);

        HttpResponse<String> ok = get(web, "/api/stats?token=" + web.getToken());
        assertEquals(200, ok.statusCode());
        assertTrue(ok.body().contains("\"mspt\""));
        assertTrue(ok.body().contains("\"nether\""));

        assertEquals(403, get(web, "/api/stats").statusCode());
        assertEquals(403, get(web, "/api/stats?token=wrong").statusCode());
        assertEquals(200, get(web, "/?token=" + web.getToken()).statusCode());
        assertEquals(404, get(web, "/secret?token=" + web.getToken()).statusCode());
    }

    @Test
    void tokenSurvivesRestartsAndCanBeReset() {
        PerformanceWebServer web = plugin.getPerformanceWebServer();
        String first = web.ensureToken(false);
        assertEquals(first, web.ensureToken(false));
        String fresh = web.ensureToken(true);
        assertFalse(first.equals(fresh));
        assertEquals(fresh, plugin.getConfigManager().getData().getString("web.token"));
    }

    @Test
    void websiteCanBeOpenedWithoutTokenIfConfigured() throws Exception {
        PerformanceWebServer web = startWeb(false);
        assertEquals(200, get(web, "/api/stats").statusCode());
    }

    @Test
    void websiteStaysOffByDefault() {
        plugin.getPerformanceWebServer().start();
        assertFalse(plugin.getPerformanceWebServer().isRunning());
    }

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

    @Test
    void monitorKeepsAnHourOfHistory() {
        PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        for (int i = 0; i < PerformanceMonitor.HISTORY_SIZE + 50; i++) {
            monitor.sample();
        }
        assertEquals(PerformanceMonitor.HISTORY_SIZE, monitor.history()[0].length);
    }
}
