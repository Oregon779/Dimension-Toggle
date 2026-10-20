package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.manager.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Chat side of the lag tools: clickable chunk lists (click = teleport there),
// staff alerts and the teleport itself. Shared by /lagmap, /dt redstone and
// /dt chunkloaders so all three look and behave the same.
public class ChunkReports {

    public static final int TOP = 10;

    private final DimensionToggle plugin;

    public ChunkReports(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    public void sendLagmap(CommandSender sender, LagMapScanner.Sort sort) {
        int limit = Math.max(1, plugin.getConfigManager().getConfig().getInt("lagmap.top", TOP));
        List<LagMapScanner.ChunkStats> top = plugin.getLagMapScanner().top(sort, limit);
        messages().sendRaw(sender, "lagmap-header", Map.of("count", String.valueOf(limit),
                "sort", messages().get("sort-" + sort.name().toLowerCase().replace('_', '-'))));
        if (top.isEmpty()) {
            messages().sendRaw(sender, "lagmap-empty");
            return;
        }
        int rank = 1;
        for (LagMapScanner.ChunkStats stats : top) {
            Map<String, String> p = base(rank++, stats.world(), stats.x(), stats.z());
            p.put("score", String.valueOf(stats.score()));
            p.put("entities", String.valueOf(stats.entities()));
            p.put("mobs", String.valueOf(stats.mobs()));
            p.put("items", String.valueOf(stats.items()));
            p.put("blocks", String.valueOf(stats.blockEntities()));
            p.put("hoppers", String.valueOf(stats.hoppers()));
            sendLine(sender, "lagmap-line", stats.world(), stats.x(), stats.z(), p);
        }
    }

    public void sendRedstone(CommandSender sender) {
        List<RedstoneMeter.ChunkRate> top = plugin.getRedstoneMeter().top(TOP);
        messages().sendRaw(sender, "redstone-header", Map.of("window", String.valueOf(RedstoneMeter.WINDOW_SECONDS)));
        if (top.isEmpty()) {
            messages().sendRaw(sender, "redstone-empty");
            return;
        }
        int rank = 1;
        for (RedstoneMeter.ChunkRate rate : top) {
            Map<String, String> p = base(rank++, rate.world(), rate.x(), rate.z());
            p.put("rate", String.valueOf(rate.perSecond()));
            sendLine(sender, "redstone-line", rate.world(), rate.x(), rate.z(), p);
        }
    }

    public void sendChunkLoaders(CommandSender sender) {
        List<ChunkLoaderDetector.LoadedChunk> found = plugin.getChunkLoaderDetector().find();
        messages().sendRaw(sender, "chunkloaders-header", Map.of("count", String.valueOf(found.size())));
        if (found.isEmpty()) {
            messages().sendRaw(sender, "chunkloaders-empty");
            return;
        }
        int rank = 1;
        for (ChunkLoaderDetector.LoadedChunk chunk : found.subList(0, Math.min(TOP, found.size()))) {
            Map<String, String> p = base(rank++, chunk.world(), chunk.x(), chunk.z());
            p.put("reason", reasonText(chunk));
            p.put("entities", String.valueOf(chunk.entities()));
            sendLine(sender, "chunkloaders-line", chunk.world(), chunk.x(), chunk.z(), p);
        }
    }

    public String reasonText(ChunkLoaderDetector.LoadedChunk chunk) {
        String key = "reason-" + chunk.reason().name().toLowerCase().replace('_', '-');
        return messages().get(key).replace("%plugins%", chunk.detail());
    }

    public void sendAlert(Collection<? extends Player> targets, String key, String world, int x, int z,
                          Map<String, String> extra) {
        Map<String, String> p = base(0, world, x, z);
        p.putAll(extra);
        Component line = clickable(messages().getPrefix() + messages().get(key), world, x, z, p);
        for (Player player : targets) {
            player.sendMessage(line);
        }
    }

    private static Map<String, String> base(int rank, String world, int x, int z) {
        Map<String, String> p = new HashMap<>();
        p.put("rank", String.valueOf(rank));
        p.put("world", world);
        p.put("x", String.valueOf(x));
        p.put("z", String.valueOf(z));
        return p;
    }

    private void sendLine(CommandSender sender, String key, String world, int x, int z, Map<String, String> p) {
        sender.sendMessage(clickable(messages().get(key), world, x, z, p));
    }

    private Component clickable(String raw, String world, int x, int z, Map<String, String> p) {
        return messages().parseWithPlaceholders(raw, p)
                .clickEvent(ClickEvent.runCommand("/dt tpchunk " + world + " " + x + " " + z))
                .hoverEvent(HoverEvent.showText(messages().parse(messages().get("chunk-hover"))));
    }

    // Teleports to the middle of a chunk, onto solid ground. In the Nether
    // the highest block is the bedrock roof, so search upwards from below.
    public boolean teleport(Player player, String worldName, int chunkX, int chunkZ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return false;
        }
        int bx = (chunkX << 4) + 8;
        int bz = (chunkZ << 4) + 8;
        int y = world.getEnvironment() == World.Environment.NETHER
                ? netherSafeY(world, bx, bz)
                : world.getHighestBlockYAt(bx, bz) + 1;
        player.teleportAsync(new Location(world, bx + 0.5, y, bz + 0.5, player.getLocation().getYaw(),
                player.getLocation().getPitch()));
        return true;
    }

    private static int netherSafeY(World world, int x, int z) {
        for (int y = 32; y < 120; y++) {
            Block floor = world.getBlockAt(x, y - 1, z);
            if (floor.getType().isSolid() && world.getBlockAt(x, y, z).isPassable()
                    && world.getBlockAt(x, y + 1, z).isPassable()) {
                return y;
            }
        }
        return 64;
    }
}
