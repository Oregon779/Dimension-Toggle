package net.dimensiontoggle.performance.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.performance.LagMapScanner;
import net.dimensiontoggle.performance.PerformanceMonitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// A small read-only status page (TPS, MSPT, memory, worlds, ...). It runs
// on its own thread and only serves the snapshot the PerformanceMonitor
// published on the main thread - it never calls the Bukkit API itself.
// Access needs the token from /dt web unless require-token is switched off.
public class PerformanceWebServer {

    private final DimensionToggle plugin;
    private HttpServer server;
    private ExecutorService executor;
    private byte[] page;
    private volatile String token;
    private volatile boolean requireToken;
    private volatile String language = "en";

    public PerformanceWebServer(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return server != null;
    }

    public int getPort() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    public String getToken() {
        return token;
    }

    public synchronized void start() {
        stop();
        var config = plugin.getConfigManager().getConfig();
        if (!config.getBoolean("web.enabled", false)) {
            return;
        }
        token = ensureToken(false);
        requireToken = config.getBoolean("web.require-token", true);
        language = plugin.getConfigManager().getActiveLanguage();
        try (InputStream in = plugin.getResource("web/index.html")) {
            page = in == null ? "<h1>DimensionToggle</h1>".getBytes(StandardCharsets.UTF_8) : in.readAllBytes();
        } catch (IOException e) {
            page = "<h1>DimensionToggle</h1>".getBytes(StandardCharsets.UTF_8);
        }
        String bind = config.getString("web.bind-address", "0.0.0.0");
        int port = config.getInt("web.port", 8105);
        try {
            HttpServer created = HttpServer.create(new InetSocketAddress(bind, port), 16);
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "DimensionToggle-Web");
                thread.setDaemon(true);
                return thread;
            });
            created.setExecutor(executor);
            created.createContext("/", this::handle);
            created.start();
            server = created;
            plugin.getLogger().info("Performance website running on port " + getPort() + ".");
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().warning("Could not start the performance website on " + bind + ":" + port
                    + " (" + e.getMessage() + "). Is the port free and allowed by your host?");
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    // Token lives in data.yml so links keep working across restarts.
    public String ensureToken(boolean regenerate) {
        var data = plugin.getConfigManager().getData();
        String current = data.getString("web.token");
        if (regenerate || current == null || current.length() < 16) {
            byte[] bytes = new byte[18];
            new SecureRandom().nextBytes(bytes);
            current = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            data.set("web.token", current);
            plugin.getConfigManager().saveData();
        }
        token = current;
        return current;
    }

    public String buildLink() {
        String base = plugin.getConfigManager().getConfig().getString("web.public-url", "");
        if (base == null || base.isBlank()) {
            String ip = plugin.getServer().getIp();
            base = "http://" + (ip == null || ip.isBlank() ? "<server-ip>" : ip) + ":" + getPort();
        }
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return requireToken ? base + "/?token=" + token : base + "/";
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "text/plain", "Method not allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (!path.equals("/") && !path.equals("/api/stats")) {
                respond(exchange, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (requireToken && !tokenMatches(queryParam(exchange.getRequestURI().getRawQuery(), "token"))) {
                respond(exchange, 403, "text/plain", "Forbidden - open the link from /dt web".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (path.equals("/")) {
                respond(exchange, 200, "text/html; charset=utf-8", page);
            } else {
                respond(exchange, 200, "application/json; charset=utf-8",
                        statsJson().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private boolean tokenMatches(String given) {
        String expected = token;
        return given != null && expected != null && MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String part : rawQuery.split("&")) {
            int eq = part.indexOf('=');
            String key = eq < 0 ? part : part.substring(0, eq);
            if (key.equals(name)) {
                return eq < 0 ? "" : URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void respond(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    String statsJson() {
        PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        PerformanceMonitor.Snapshot s = monitor.latest();
        double[][] history = monitor.history();

        JsonObject root = new JsonObject();
        root.addProperty("language", language);
        root.addProperty("timestamp", s.timestamp());
        root.addProperty("tps1", s.tps1());
        root.addProperty("tps5", s.tps5());
        root.addProperty("tps15", s.tps15());
        root.addProperty("mspt", s.mspt());
        root.addProperty("msptMax", s.msptMax());
        root.addProperty("online", s.online());
        root.addProperty("maxPlayers", s.maxPlayers());
        root.addProperty("memoryUsedMb", s.usedMemoryMb());
        root.addProperty("memoryMaxMb", s.maxMemoryMb());
        root.addProperty("cpuPercent", s.cpuPercent());
        root.addProperty("uptimeSeconds", s.uptimeSeconds());
        root.addProperty("autoPerformance", s.autoPerformanceActive());
        root.addProperty("sampleSeconds", PerformanceMonitor.SAMPLE_INTERVAL_TICKS / 20);

        JsonArray worlds = new JsonArray();
        for (PerformanceMonitor.WorldStats w : s.worlds()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name());
            o.addProperty("environment", w.environment());
            o.addProperty("players", w.players());
            o.addProperty("chunks", w.chunks());
            o.addProperty("entities", w.entities());
            o.addProperty("blockEntities", w.blockEntities());
            worlds.add(o);
        }
        root.add("worlds", worlds);

        JsonObject dimensions = new JsonObject();
        for (Map.Entry<String, String> entry : s.dimensions().entrySet()) {
            dimensions.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("dimensions", dimensions);

        JsonArray chunks = new JsonArray();
        for (LagMapScanner.ChunkStats c : s.topChunks()) {
            JsonObject o = new JsonObject();
            o.addProperty("world", c.world());
            o.addProperty("x", c.x());
            o.addProperty("z", c.z());
            o.addProperty("entities", c.entities());
            o.addProperty("mobs", c.mobs());
            o.addProperty("items", c.items());
            o.addProperty("score", c.score());
            chunks.add(o);
        }
        root.add("topChunks", chunks);

        root.add("msptHistory", toJson(history[0]));
        root.add("tpsHistory", toJson(history[1]));
        return root.toString();
    }

    private static JsonArray toJson(double[] values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            array.add(value);
        }
        return array;
    }
}
