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
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class DimensionToggle extends JavaPlugin {

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

        getLogger().info("Config loaded (" + safeLanguage() + "), Nether "
                + (dimensionManager.isEnabled(ToggleDimension.NETHER) ? "enabled" : "disabled")
                + " / End " + (dimensionManager.isEnabled(ToggleDimension.END) ? "enabled" : "disabled")
                + " - commands, listeners, GUI and update checker ready.");
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
}
