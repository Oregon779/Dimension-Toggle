package net.dimensiontoggle;

import net.dimensiontoggle.command.DimensionToggleCommand;
import net.dimensiontoggle.config.ConfigManager;
import net.dimensiontoggle.config.GuiConfigManager;
import net.dimensiontoggle.gui.GuiManager;
import net.dimensiontoggle.io.IoExecutor;
import net.dimensiontoggle.listener.PortalListener;
import net.dimensiontoggle.manager.DashboardStatsManager;
import net.dimensiontoggle.manager.DimensionManager;
import net.dimensiontoggle.manager.ElytraFlyManager;
import net.dimensiontoggle.manager.KeepInventoryManager;
import net.dimensiontoggle.manager.LogManager;
import net.dimensiontoggle.manager.MaintenanceManager;
import net.dimensiontoggle.manager.MessageManager;
import net.dimensiontoggle.manager.MobManagementManager;
import net.dimensiontoggle.manager.MobSpawnManager;
import net.dimensiontoggle.manager.NotificationManager;
import net.dimensiontoggle.manager.PeakPlayerManager;
import net.dimensiontoggle.manager.PvpIntegrationManager;
import net.dimensiontoggle.manager.ScheduleManager;
import net.dimensiontoggle.manager.SoundManager;
import net.dimensiontoggle.manager.SpawnerToggleManager;
import net.dimensiontoggle.manager.UpdateChecker;
import net.dimensiontoggle.model.ToggleDimension;
import net.dimensiontoggle.performance.AfkViewManager;
import net.dimensiontoggle.performance.AutoPerformanceManager;
import net.dimensiontoggle.performance.ChunkLoaderDetector;
import net.dimensiontoggle.performance.ChunkReports;
import net.dimensiontoggle.performance.EntityCapListener;
import net.dimensiontoggle.performance.FarmLimiter;
import net.dimensiontoggle.performance.FreezeListener;
import net.dimensiontoggle.performance.ItemCleaner;
import net.dimensiontoggle.performance.LagMapScanner;
import net.dimensiontoggle.performance.PerformanceMonitor;
import net.dimensiontoggle.performance.RedstoneMeter;
import net.dimensiontoggle.performance.VillagerOptimizer;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class DimensionToggle extends JavaPlugin {

    // bStats plugin id from https://bstats.org/what-is-my-plugin-id
    // (register the plugin there once). 0 = metrics are not sent.
    static final int BSTATS_PLUGIN_ID = 0;

    private static DimensionToggle instance;

    private IoExecutor ioExecutor;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private SoundManager soundManager;
    private NotificationManager notificationManager;
    private DimensionManager dimensionManager;
    private LogManager logManager;
    private MaintenanceManager maintenanceManager;
    private ScheduleManager scheduleManager;
    private UpdateChecker updateChecker;
    private KeepInventoryManager keepInventoryManager;
    private ElytraFlyManager elytraFlyManager;
    private PeakPlayerManager peakPlayerManager;
    private GuiManager guiManager;
    private GuiConfigManager guiConfigManager;
    private MobSpawnManager mobSpawnManager;
    private MobManagementManager mobManagementManager;
    private PvpIntegrationManager pvpIntegrationManager;
    private SpawnerToggleManager spawnerToggleManager;
    private DashboardStatsManager dashboardStatsManager;
    private PerformanceMonitor performanceMonitor;
    private LagMapScanner lagMapScanner;
    private RedstoneMeter redstoneMeter;
    private ChunkLoaderDetector chunkLoaderDetector;
    private ChunkReports chunkReports;
    private FarmLimiter farmLimiter;
    private VillagerOptimizer villagerOptimizer;
    private ItemCleaner itemCleaner;
    private AfkViewManager afkViewManager;
    private AutoPerformanceManager autoPerformanceManager;

    @Override
    public void onEnable() {
        instance = this;

        // First: ConfigManager may already save data.yml while loading.
        this.ioExecutor = new IoExecutor(getLogger());
        this.configManager = new ConfigManager(this);
        this.configManager.loadAll();

        this.messageManager = new MessageManager(this);
        this.logManager = new LogManager(this);
        this.soundManager = new SoundManager(this);
        this.notificationManager = new NotificationManager(this);
        this.dimensionManager = new DimensionManager(this);
        this.maintenanceManager = new MaintenanceManager(this);
        this.scheduleManager = new ScheduleManager(this);
        this.updateChecker = new UpdateChecker(this);
        this.keepInventoryManager = new KeepInventoryManager(this);
        this.elytraFlyManager = new ElytraFlyManager(this);
        this.peakPlayerManager = new PeakPlayerManager(this);
        this.mobSpawnManager = new MobSpawnManager(this);
        this.mobManagementManager = new MobManagementManager(this);
        this.pvpIntegrationManager = new PvpIntegrationManager(this);
        this.spawnerToggleManager = new SpawnerToggleManager(this);
        this.dashboardStatsManager = new DashboardStatsManager(this);
        this.performanceMonitor = new PerformanceMonitor(this);
        this.lagMapScanner = new LagMapScanner(this);
        this.redstoneMeter = new RedstoneMeter(this);
        this.chunkLoaderDetector = new ChunkLoaderDetector(this);
        this.chunkReports = new ChunkReports(this);
        this.autoPerformanceManager = new AutoPerformanceManager(this);
        this.farmLimiter = new FarmLimiter(this);
        this.villagerOptimizer = new VillagerOptimizer(this);
        this.itemCleaner = new ItemCleaner(this);
        this.afkViewManager = new AfkViewManager(this);
        this.guiConfigManager = new GuiConfigManager(this);
        this.guiConfigManager.loadAll();
        this.guiManager = new GuiManager(this);

        registerCommand();
        registerListeners();
        pvpIntegrationManager.applyStoredState();
        scheduleManager.start();
        updateChecker.start();
        peakPlayerManager.start();
        mobManagementManager.start();
        dashboardStatsManager.start();
        guiManager.start();
        startPerformanceFeatures();
        hookPlaceholderApi();
        startMetrics();

        getLogger().info("Config loaded (" + safeLanguage() + "), Nether "
                + (dimensionManager.isEnabled(ToggleDimension.NETHER) ? "enabled" : "disabled")
                + " / End " + (dimensionManager.isEnabled(ToggleDimension.END) ? "enabled" : "disabled")
                + " - commands, listeners, GUI and update checker ready.");
    }

    // Also used by /dt reload so changed settings take effect immediately.
    public void startPerformanceFeatures() {
        performanceMonitor.start();
        redstoneMeter.start();
        autoPerformanceManager.start();
        farmLimiter.start();
        villagerOptimizer.start();
        itemCleaner.start();
        afkViewManager.start();
    }

    private void stopPerformanceFeatures() {
        afkViewManager.stop();
        itemCleaner.stop();
        villagerOptimizer.stop();
        farmLimiter.stop();
        autoPerformanceManager.stop();
        redstoneMeter.stop();
        performanceMonitor.stop();
    }

    private void hookPlaceholderApi() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new net.dimensiontoggle.hook.PlaceholderHook(this).register();
            getLogger().info("PlaceholderAPI found - %dimensiontoggle_...% placeholders registered.");
        }
    }

    private void startMetrics() {
        if (BSTATS_PLUGIN_ID <= 0) {
            return;
        }
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("language", this::safeLanguage));
        metrics.addCustomChart(new SimplePie("nether_enabled",
                () -> String.valueOf(dimensionManager.isEnabled(ToggleDimension.NETHER))));
        metrics.addCustomChart(new SimplePie("end_enabled",
                () -> String.valueOf(dimensionManager.isEnabled(ToggleDimension.END))));
        metrics.addCustomChart(new SimplePie("farm_limiter", () -> String.valueOf(farmLimiter.isConfiguredOn())));
        metrics.addCustomChart(new SimplePie("afk_view", () -> String.valueOf(afkViewManager.isConfiguredOn())));
    }

    private String safeLanguage() {
        try {
            return configManager.getActiveLanguage();
        } catch (Exception e) {
            return "en";
        }
    }

    @Override
    public void onDisable() {
        // Player-facing state that would otherwise outlive the plugin: open
        // menus (no longer click-protected once disabled) and boss bars
        // (their scheduled hide tasks are cancelled with the plugin).
        if (guiManager != null) {
            guiManager.closeAllMenus();
        }
        if (notificationManager != null) {
            notificationManager.hideAllBossBars();
        }
        if (maintenanceManager != null) {
            maintenanceManager.shutdown();
        }
        if (scheduleManager != null) {
            scheduleManager.stop();
        }
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
        if (peakPlayerManager != null) {
            peakPlayerManager.stop();
        }
        if (mobManagementManager != null) {
            mobManagementManager.stop();
        }
        if (dashboardStatsManager != null) {
            dashboardStatsManager.stop();
        }
        if (guiManager != null) {
            guiManager.stop();
        }
        if (performanceMonitor != null) {
            stopPerformanceFeatures();
        }
        // Let queued writes (log lines, config edits, data.yml) finish first,
        // then write the final state synchronously so nothing is lost.
        if (ioExecutor != null) {
            ioExecutor.shutdown(5000);
        }
        if (configManager != null) {
            configManager.saveDataSync();
        }
        getLogger().info("DimensionToggle has been disabled.");
    }

    private void registerCommand() {
        PluginCommand command = getCommand("dimensiontoggle");
        if (command != null) {
            DimensionToggleCommand executor = new DimensionToggleCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            // /lagmap is a shortcut for /dt lagmap - same code, same checks.
            PluginCommand lagmap = getCommand("lagmap");
            if (lagmap != null) {
                lagmap.setExecutor(executor.prefixed("lagmap"));
                lagmap.setTabCompleter(executor.prefixed("lagmap"));
            }
        } else {
            getLogger().warning("Could not register the 'dimensiontoggle' command. Check plugin.yml!");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        getServer().getPluginManager().registerEvents(elytraFlyManager, this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(mobManagementManager, this);
        getServer().getPluginManager().registerEvents(spawnerToggleManager, this);
        getServer().getPluginManager().registerEvents(pvpIntegrationManager, this);
        getServer().getPluginManager().registerEvents(redstoneMeter, this);
        getServer().getPluginManager().registerEvents(afkViewManager, this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityCapListener(this), this);
    }

    public static DimensionToggle getInstance() {
        return instance;
    }

    public IoExecutor getIoExecutor() {
        return ioExecutor;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public DimensionManager getDimensionManager() {
        return dimensionManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }

    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public KeepInventoryManager getKeepInventoryManager() {
        return keepInventoryManager;
    }

    public ElytraFlyManager getElytraFlyManager() {
        return elytraFlyManager;
    }

    public PeakPlayerManager getPeakPlayerManager() {
        return peakPlayerManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public GuiConfigManager getGuiConfigManager() {
        return guiConfigManager;
    }

    public MobSpawnManager getMobSpawnManager() {
        return mobSpawnManager;
    }

    public MobManagementManager getMobManagementManager() {
        return mobManagementManager;
    }

    public PvpIntegrationManager getPvpIntegrationManager() {
        return pvpIntegrationManager;
    }

    public SpawnerToggleManager getSpawnerToggleManager() {
        return spawnerToggleManager;
    }

    public DashboardStatsManager getDashboardStatsManager() {
        return dashboardStatsManager;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public LagMapScanner getLagMapScanner() {
        return lagMapScanner;
    }

    public RedstoneMeter getRedstoneMeter() {
        return redstoneMeter;
    }

    public ChunkLoaderDetector getChunkLoaderDetector() {
        return chunkLoaderDetector;
    }

    public ChunkReports getChunkReports() {
        return chunkReports;
    }

    public FarmLimiter getFarmLimiter() {
        return farmLimiter;
    }

    public VillagerOptimizer getVillagerOptimizer() {
        return villagerOptimizer;
    }

    public ItemCleaner getItemCleaner() {
        return itemCleaner;
    }

    public AfkViewManager getAfkViewManager() {
        return afkViewManager;
    }

    public AutoPerformanceManager getAutoPerformanceManager() {
        return autoPerformanceManager;
    }

}
