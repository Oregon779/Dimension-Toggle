package net.dimensiontoggle.gui;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.WorldScope;
import net.dimensiontoggle.performance.PerformanceMonitor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The Overworld & performance panel: Overworld mob settings, the optional
// optimizers (all off by default) and the lag tools.
public final class OverworldGuiBuilder {

    private static final int DASHBOARD_SLOT = 31;
    private static final int[] INTERIOR_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30,  32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private OverworldGuiBuilder() {
    }

    public static Inventory build(DimensionToggle plugin) {
        FileConfiguration cfg = plugin.getGuiConfigManager().getOverworld();
        String base = "panel.";
        var config = plugin.getConfigManager().getConfig();

        Inventory inventory = Bukkit.createInventory(null, 54,
                plugin.getMessageManager().parse(cfg.getString(base + "title", "&a&lOverworld")));

        String on = cfg.getString("state-on", "&aON");
        String off = cfg.getString("state-off", "&7OFF");

        List<ItemStack> features = new ArrayList<>();
        boolean spawning = plugin.getMobSpawnManager().isEnabled(WorldScope.OVERWORLD);
        features.add(toggle(plugin, cfg, base + "mobspawn", spawning ? Material.SPAWNER : Material.BARRIER,
                "OW_MOBSPAWN", spawning, on, off, Map.of()));

        features.add(GuiItems.build(plugin, Material.ZOMBIE_HEAD, "OW_MOBMANAGEMENT",
                cfg.getString(base + "mobmanagement.name"), cfg.getStringList(base + "mobmanagement.lore")));

        boolean farm = plugin.getFarmLimiter().isConfiguredOn();
        features.add(toggle(plugin, cfg, base + "farm-limiter", farm ? Material.HAY_BLOCK : Material.WHEAT,
                "OW_FARMLIMITER", farm, on, off,
                Map.of("size", String.valueOf(config.getInt("farm-limiter.group-size", 20)))));

        boolean cap = config.getBoolean("entity-cap.enabled", false);
        features.add(toggle(plugin, cfg, base + "entity-cap", cap ? Material.IRON_BARS : Material.OAK_FENCE,
                "OW_ENTITYCAP", cap, on, off,
                Map.of("max", String.valueOf(config.getInt("entity-cap.max-per-chunk", 40)))));

        boolean villagers = plugin.getVillagerOptimizer().isEnabled();
        features.add(toggle(plugin, cfg, base + "villager-optimizer", villagers ? Material.EMERALD_BLOCK : Material.EMERALD,
                "OW_VILLAGER", villagers, on, off,
                Map.of("minutes", String.valueOf(config.getInt("villager-optimizer.idle-minutes", 5)))));

        boolean cleaner = plugin.getItemCleaner().isEnabled();
        int secondsLeft = plugin.getItemCleaner().getSecondsLeft();
        features.add(toggle(plugin, cfg, base + "item-cleaner", cleaner ? Material.HOPPER : Material.DROPPER,
                "OW_ITEMCLEANER", cleaner, on, off,
                Map.of("minutes", String.valueOf(config.getInt("item-cleaner.interval-minutes", 10)),
                        "next", secondsLeft < 0 ? "-" : plugin.getMessageManager().formatDuration(secondsLeft))));

        boolean afk = plugin.getAfkViewManager().isConfiguredOn();
        features.add(toggle(plugin, cfg, base + "afk-view", afk ? Material.ENDER_EYE : Material.ENDER_PEARL,
                "OW_AFKVIEW", afk, on, off,
                Map.of("minutes", String.valueOf(config.getInt("afk-view.afk-minutes", 5)),
                        "distance", String.valueOf(config.getInt("afk-view.view-distance", 2)),
                        "count", String.valueOf(plugin.getAfkViewManager().countReduced()))));

        boolean auto = plugin.getAutoPerformanceManager().isEnabled();
        boolean autoActive = plugin.getAutoPerformanceManager().isActive();
        features.add(toggle(plugin, cfg, base + "auto-performance",
                autoActive ? Material.REDSTONE_BLOCK : auto ? Material.COMPARATOR : Material.REPEATER,
                "OW_AUTOPERF", auto, on, off,
                Map.of("mspt", String.valueOf(config.getDouble("auto-performance.activate-mspt", 45)),
                        "active", cfg.getString(autoActive ? "auto-active" : "auto-idle", ""))));

        features.add(GuiItems.build(plugin, Material.FILLED_MAP, "OW_LAGMAP",
                cfg.getString(base + "lagmap.name"), cfg.getStringList(base + "lagmap.lore")));
        features.add(GuiItems.build(plugin, Material.REDSTONE, "OW_REDSTONE",
                cfg.getString(base + "redstone.name"), cfg.getStringList(base + "redstone.lore")));
        features.add(GuiItems.build(plugin, Material.LODESTONE, "OW_CHUNKLOADERS",
                cfg.getString(base + "chunkloaders.name"), cfg.getStringList(base + "chunkloaders.lore")));

        boolean web = plugin.getPerformanceWebServer().isRunning();
        features.add(GuiItems.build(plugin, web ? Material.SPYGLASS : Material.GRAY_DYE, "OW_WEB",
                cfg.getString(base + "web." + (web ? "name-on" : "name-off")),
                cfg.getStringList(base + "web." + (web ? "lore-on" : "lore-off")), web));

        for (int i = 0; i < features.size() && i < INTERIOR_SLOTS.length; i++) {
            inventory.setItem(INTERIOR_SLOTS[i], features.get(i));
        }

        inventory.setItem(DASHBOARD_SLOT, dashboard(plugin, cfg, base));
        inventory.setItem(49, GuiItems.build(plugin, Material.ARROW, "OW_BACK",
                cfg.getString(base + "back-button.name"), cfg.getStringList(base + "back-button.lore")));

        GuiItems.fillBorderAndFiller(inventory, plugin, GuiItems.fillerFor(WorldScope.OVERWORLD),
                GuiItems.borderFor(WorldScope.OVERWORLD), 6);
        return inventory;
    }

    private static ItemStack toggle(DimensionToggle plugin, FileConfiguration cfg, String path, Material material,
                                    String action, boolean enabled, String on, String off, Map<String, String> extra) {
        Map<String, String> placeholders = new HashMap<>(extra);
        placeholders.put("state", enabled ? on : off);
        return GuiItems.build(plugin, material, action,
                GuiItems.sub(cfg.getString(path + ".name"), placeholders),
                GuiItems.subList(cfg.getStringList(path + ".lore"), placeholders), enabled);
    }

    private static ItemStack dashboard(DimensionToggle plugin, FileConfiguration cfg, String base) {
        PerformanceMonitor.Snapshot s = plugin.getPerformanceMonitor().latest();
        Map<String, String> p = new HashMap<>();
        p.put("tps", String.format("%.1f", s.tps1()));
        p.put("mspt", String.format("%.1f", s.mspt()));
        p.put("players", s.online() + "/" + s.maxPlayers());
        p.put("memory", s.usedMemoryMb() + "/" + s.maxMemoryMb() + " MB");
        p.put("chunks", String.valueOf(s.worlds().stream().mapToInt(PerformanceMonitor.WorldStats::chunks).sum()));
        p.put("entities", String.valueOf(s.worlds().stream().mapToInt(PerformanceMonitor.WorldStats::entities).sum()));
        p.put("afk", String.valueOf(plugin.getAfkViewManager().countReduced()));
        return GuiItems.build(plugin, Material.CLOCK, null, cfg.getString(base + "dashboard.name"),
                GuiItems.subList(cfg.getStringList(base + "dashboard.lore"), p));
    }
}
