package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.performance.ChunkLoaderDetector;
import net.dimensiontoggle.performance.ChunkReports;
import net.dimensiontoggle.performance.LagMapScanner;
import net.dimensiontoggle.performance.RedstoneMeter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// One menu for all three lag reports: every entry is a chunk; click =
// teleport there. The lag map additionally sorts and can clean up a chunk
// (shift-click twice).
public final class ChunkReportGuiBuilder {

    public enum Mode { LAG, REDSTONE, LOADERS }

    static final int[] ENTRY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private ChunkReportGuiBuilder() {
    }

    // Action format: CR_TP|world|x|z (world names can't contain '|').
    static String tpAction(String world, int x, int z) {
        return "CR_TP|" + world + "|" + x + "|" + z;
    }

    static String chunkId(String world, int x, int z) {
        return world + "|" + x + "|" + z;
    }

    public static Inventory build(DimensionToggle plugin, Mode mode, LagMapScanner.Sort sort, String pendingCleanup) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getOverworld();
        String base = "reports." + mode.name().toLowerCase() + ".";
        Inventory inventory = Bukkit.createInventory(null, 45,
                plugin.getMessageManager().parse(cfg.getString(base + "title", "&6&lReport")));

        int slot = 0;
        switch (mode) {
            case LAG -> {
                int limit = Math.min(ENTRY_SLOTS.length,
                        Math.max(1, plugin.getConfigManager().getConfig().getInt("lagmap.top", ChunkReports.TOP)));
                int rank = 1;
                for (LagMapScanner.ChunkStats c : plugin.getLagMapScanner().top(sort, limit)) {
                    Map<String, String> p = base(rank, c.world(), c.x(), c.z());
                    p.put("score", String.valueOf(c.score()));
                    p.put("entities", String.valueOf(c.entities()));
                    p.put("mobs", String.valueOf(c.mobs()));
                    p.put("items", String.valueOf(c.items()));
                    p.put("blocks", String.valueOf(c.blockEntities()));
                    p.put("hoppers", String.valueOf(c.hoppers()));
                    List<String> lore = new ArrayList<>(GuiItems.subList(cfg.getStringList(base + "entry-lore"), p));
                    boolean pending = chunkId(c.world(), c.x(), c.z()).equals(pendingCleanup);
                    lore.addAll(cfg.getStringList(base + (pending ? "confirm-lore" : "action-lore")));
                    inventory.setItem(ENTRY_SLOTS[slot++], GuiItems.build(plugin, icon(c.world(), pending),
                            tpAction(c.world(), c.x(), c.z()), GuiItems.sub(cfg.getString(base + "entry-name"), p), lore, pending));
                    rank++;
                }
                Map<String, String> sortPlaceholder = Map.of("sort",
                        plugin.getMessageManager().get("sort-" + sort.name().toLowerCase().replace('_', '-')));
                inventory.setItem(39, GuiItems.build(plugin, Material.COMPARATOR, "CR_SORT",
                        GuiItems.sub(cfg.getString(base + "sort-button.name"), sortPlaceholder),
                        GuiItems.subList(cfg.getStringList(base + "sort-button.lore"), sortPlaceholder)));
            }
            case REDSTONE -> {
                int rank = 1;
                for (RedstoneMeter.ChunkRate r : plugin.getRedstoneMeter().top(ENTRY_SLOTS.length)) {
                    Map<String, String> p = base(rank++, r.world(), r.x(), r.z());
                    p.put("rate", String.valueOf(r.perSecond()));
                    p.put("window", String.valueOf(RedstoneMeter.WINDOW_SECONDS));
                    inventory.setItem(ENTRY_SLOTS[slot++], GuiItems.build(plugin, Material.REDSTONE,
                            tpAction(r.world(), r.x(), r.z()), GuiItems.sub(cfg.getString(base + "entry-name"), p),
                            GuiItems.subList(cfg.getStringList(base + "entry-lore"), p)));
                }
            }
            case LOADERS -> {
                int rank = 1;
                for (ChunkLoaderDetector.LoadedChunk c : plugin.getChunkLoaderDetector().find()) {
                    if (slot >= ENTRY_SLOTS.length) {
                        break;
                    }
                    Map<String, String> p = base(rank++, c.world(), c.x(), c.z());
                    p.put("reason", plugin.getChunkReports().reasonText(c));
                    p.put("entities", String.valueOf(c.entities()));
                    inventory.setItem(ENTRY_SLOTS[slot++], GuiItems.build(plugin, Material.LODESTONE,
                            tpAction(c.world(), c.x(), c.z()), GuiItems.sub(cfg.getString(base + "entry-name"), p),
                            GuiItems.subList(cfg.getStringList(base + "entry-lore"), p)));
                }
            }
        }

        if (slot == 0) {
            inventory.setItem(22, GuiItems.build(plugin, Material.BARRIER, null,
                    cfg.getString(base + "empty"), null));
        }

        inventory.setItem(40, GuiItems.build(plugin, Material.ARROW, "CR_BACK",
                cfg.getString("reports.back-button.name"), DimensionGuiBuilder.lore(cfg, "reports.back-button.lore", Map.of())));
        inventory.setItem(41, GuiItems.build(plugin, Material.SUNFLOWER, "CR_REFRESH",
                cfg.getString("reports.refresh-button.name"), DimensionGuiBuilder.lore(cfg, "reports.refresh-button.lore", Map.of())));

        GuiItems.frame(inventory, plugin, GuiItems.Theme.OVERWORLD, 5);
        return inventory;
    }

    private static Map<String, String> base(int rank, String world, int x, int z) {
        Map<String, String> p = new HashMap<>();
        p.put("rank", String.valueOf(rank));
        p.put("world", world);
        p.put("x", String.valueOf(x));
        p.put("z", String.valueOf(z));
        return p;
    }

    private static Material icon(String worldName, boolean pending) {
        if (pending) {
            return Material.TNT;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Material.MAP;
        }
        return switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }
}
