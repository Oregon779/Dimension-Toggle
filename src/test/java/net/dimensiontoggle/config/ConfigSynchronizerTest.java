package net.dimensiontoggle.config;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.PluginTestBase;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The whole flow as a server owner experiences it: an existing (older)
// config.yml meets a newer plugin version.
class ConfigSynchronizerTest extends PluginTestBase {

    private File config() {
        return new File(plugin.getDataFolder(), "config.yml");
    }

    private File stateFile() {
        return new File(plugin.getDataFolder(), ConfigSynchronizer.STATE_FILE);
    }

    private String read() throws Exception {
        return Files.readString(config().toPath(), StandardCharsets.UTF_8);
    }

    private void write(String content) throws Exception {
        Files.writeString(config().toPath(), content, StandardCharsets.UTF_8);
    }

    private void restart() {
        server.getPluginManager().disablePlugin(plugin);
        plugin = MockBukkit.load(DimensionToggle.class);
        plugin.getUpdateChecker().stop();
    }

    private List<String> current() throws Exception {
        return Files.readAllLines(config().toPath(), StandardCharsets.UTF_8);
    }

    // Removes an option from the file like an older plugin version would
    // never have written it.
    private void drop(String... keys) throws Exception {
        List<String> without = ConfigUpdater.remove(current(), ConfigUpdater.join(keys));
        Files.write(config().toPath(), without, StandardCharsets.UTF_8);
    }

    // Makes the plugin "forget" it ever knew an option - exactly the state
    // after updating to a version that introduced it.
    private void forget(String dottedPath) throws Exception {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile());
        String key = ConfigSynchronizer.id("config.yml") + ".known";
        List<String> known = new java.util.ArrayList<>(state.getStringList(key));
        known.removeIf(p -> p.equals(dottedPath) || p.startsWith(dottedPath + "."));
        state.set(key, known);
        Files.writeString(stateFile().toPath(), state.saveToString(), StandardCharsets.UTF_8);
    }

    @Test
    void freshInstallRemembersEveryOption() {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile());
        List<String> known = state.getStringList(ConfigSynchronizer.id("config.yml") + ".known");
        assertTrue(known.contains("farm-limiter.group-size"));
        assertEquals(ConfigMigrations.latestVersion("config.yml"),
                state.getInt(ConfigSynchronizer.id("config.yml") + ".version"));
    }

    @Test
    void newOptionsOfAnUpdateAreAddedAndOwnValuesKept() throws Exception {
        // The admin's own settings and comment ...
        write(read().replace("language: \"en\"", "language: \"de\"  # ours")
                .replace("  group-size: 20", "  group-size: 33"));
        // ... and an update that brings two new options.
        drop("farm-limiter", "check-interval-seconds");
        drop("auto-performance");
        forget("farm-limiter.check-interval-seconds");
        forget("auto-performance");

        restart();

        String content = read();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config());
        assertEquals(30, yaml.getInt("farm-limiter.check-interval-seconds"), "new option, default value");
        assertEquals(35.0, yaml.getDouble("auto-performance.recover-mspt"));
        assertEquals(33, yaml.getInt("farm-limiter.group-size"), "own value kept");
        assertEquals("de", yaml.getString("language"));
        assertTrue(content.contains("# ours"), "own comment kept");
        assertTrue(content.indexOf("check-interval-seconds") > content.indexOf("group-size: 33"),
                "added inside its own section, after its neighbour");
        // ... and the plugin actually uses the merged file.
        assertEquals(30, plugin.getConfigManager().getConfig().getInt("farm-limiter.check-interval-seconds"));
        assertEquals("de", plugin.getConfigManager().getActiveLanguage());
    }

    @Test
    void optionsTheAdminDeletedStayDeleted() throws Exception {
        drop("item-cleaner", "warnings");
        restart();
        assertFalse(YamlConfiguration.loadConfiguration(config()).contains("item-cleaner.warnings"));
    }

    @Test
    void firstRunWithoutStateAddsEverythingMissingOnce() throws Exception {
        drop("schedule", "nether", "countdown-enabled");
        drop("auto-performance");
        Files.delete(stateFile().toPath());   // like an upgrade from 3.2.0

        restart();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config());
        assertTrue(yaml.getBoolean("schedule.nether.countdown-enabled"));
        assertEquals(45.0, yaml.getDouble("auto-performance.activate-mspt"));
        assertTrue(stateFile().exists());
    }

    @Test
    void aBackupIsWrittenBeforeChangingAndOnlyTheLastFiveAreKept() throws Exception {
        String before = read();
        drop("auto-performance", "recover-mspt");
        forget("auto-performance.recover-mspt");
        String changed = read();
        restart();

        File[] backups = new File(plugin.getDataFolder(), ConfigSynchronizer.BACKUP_FOLDER)
                .listFiles((d, n) -> n.startsWith("config-"));
        assertEquals(1, backups.length);
        assertEquals(changed, Files.readString(backups[0].toPath(), StandardCharsets.UTF_8));
        assertFalse(before.equals(changed));

        for (int i = 0; i < 7; i++) {
            drop("auto-performance", "recover-mspt");
            forget("auto-performance.recover-mspt");
            plugin.getConfigManager().reloadAll();
        }
        assertEquals(ConfigSynchronizer.BACKUPS_KEPT, new File(plugin.getDataFolder(), ConfigSynchronizer.BACKUP_FOLDER)
                .listFiles((d, n) -> n.startsWith("config-")).length);
    }

    @Test
    void unchangedFilesAreNotRewrittenOrBackedUp() throws Exception {
        String before = read();
        restart();
        assertEquals(before, read());
        assertFalse(new File(plugin.getDataFolder(), ConfigSynchronizer.BACKUP_FOLDER).exists());
    }

    @Test
    void versionedMigrationRemovesTheObsoleteNotifySections() throws Exception {
        write(read().replace("maintenance:\n", "maintenance:\n  notify:\n    chat: true\n    title: false\n"));
        assertTrue(YamlConfiguration.loadConfiguration(config()).contains("maintenance.notify.chat"));
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile());
        state.set(ConfigSynchronizer.id("config.yml") + ".version", 1);
        Files.writeString(stateFile().toPath(), state.saveToString(), StandardCharsets.UTF_8);

        restart();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config());
        assertFalse(yaml.contains("maintenance.notify"));
        assertEquals("server", yaml.getString("maintenance.broadcast-scope"), "neighbours untouched");
        assertEquals(ConfigMigrations.latestVersion("config.yml"), YamlConfiguration.loadConfiguration(stateFile())
                .getInt(ConfigSynchronizer.id("config.yml") + ".version"));
    }

    @Test
    void theRemovedWebsiteIsCleanedOutOfDevBuildFiles() throws Exception {
        // Files as 3.3.0-dev3 left them.
        write(read() + """

                # A small read-only website with TPS, MSPT, memory, worlds and the busiest
                # chunks. /dt web shows the link.
                web:
                  enabled: true
                  port: 8105
                """);
        File messages = new File(plugin.getDataFolder(), "languages/en/messages.yml");
        Files.writeString(messages.toPath(), Files.readString(messages.toPath(), StandardCharsets.UTF_8)
                + "help-web: \"&d/dt web\"\nweb-link: \"%url%\"\nweb-disabled: \"off\"\nweb-token-reset: \"new\"\n",
                StandardCharsets.UTF_8);
        File gui = new File(plugin.getDataFolder(), "gui/overworld/config.yml");
        Files.writeString(gui.toPath(), Files.readString(gui.toPath(), StandardCharsets.UTF_8).replace("  dashboard:\n",
                "  web:\n    name-on: \"Website\"\n    lore-off:\n      - \"off\"\n\n  dashboard:\n"), StandardCharsets.UTF_8);
        plugin.getConfigManager().getData().set("web.token", "secret");
        plugin.getConfigManager().saveDataSync();
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile());
        state.set(ConfigSynchronizer.id("config.yml") + ".version", 2);
        state.set(ConfigSynchronizer.id("languages/en/messages.yml") + ".version", 1);
        state.set(ConfigSynchronizer.id("gui/overworld/config.yml") + ".version", 1);
        Files.writeString(stateFile().toPath(), state.saveToString(), StandardCharsets.UTF_8);

        restart();

        assertFalse(YamlConfiguration.loadConfiguration(config()).contains("web"));
        assertFalse(read().contains("website"), "its comment goes too");
        assertTrue(YamlConfiguration.loadConfiguration(config()).contains("auto-performance.enabled"));
        YamlConfiguration lang = YamlConfiguration.loadConfiguration(messages);
        assertFalse(lang.contains("help-web") || lang.contains("web-link") || lang.contains("web-disabled")
                || lang.contains("web-token-reset"));
        YamlConfiguration guiYaml = YamlConfiguration.loadConfiguration(gui);
        assertFalse(guiYaml.contains("panel.web"));
        assertTrue(guiYaml.contains("panel.dashboard.name"));
        assertFalse(plugin.getConfigManager().getData().contains("web"));
    }

    @Test
    void languageAndGuiFilesAreKeptUpToDateToo() throws Exception {
        File messages = new File(plugin.getDataFolder(), "languages/de/messages.yml");
        File gui = new File(plugin.getDataFolder(), "gui/overworld/config.yml");
        Files.write(messages.toPath(), ConfigUpdater.remove(Files.readAllLines(messages.toPath()),
                ConfigUpdater.join("lagmap-empty")), StandardCharsets.UTF_8);
        Files.write(gui.toPath(), ConfigUpdater.remove(Files.readAllLines(gui.toPath()),
                ConfigUpdater.join("panel", "chunkloaders", "lore")), StandardCharsets.UTF_8);
        Files.delete(stateFile().toPath());

        restart();

        assertTrue(YamlConfiguration.loadConfiguration(messages).contains("lagmap-empty"));
        assertTrue(YamlConfiguration.loadConfiguration(gui).contains("panel.chunkloaders.lore"));
    }
}
