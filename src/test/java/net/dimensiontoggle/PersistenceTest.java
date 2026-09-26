package net.dimensiontoggle;

import be.seeseemelk.mockbukkit.MockBukkit;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceTest extends PluginTestBase {

    private File dataFile() {
        return new File(plugin.getDataFolder(), "data.yml");
    }

    @Test
    void rapidStateChangesEndUpOnDiskAsTheLatestState() {
        for (int i = 0; i < 25; i++) {
            plugin.getDimensionManager().setEnabled(ToggleDimension.END, i % 2 == 0);
        }
        // Last iteration (i = 24) enabled it; flip once more to end on "disabled".
        plugin.getDimensionManager().setEnabled(ToggleDimension.END, false);
        plugin.getIoExecutor().drain(5000);

        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(dataFile());
        assertFalse(onDisk.getBoolean("dimensions.end.enabled", true));
    }

    @Test
    void corruptDataFileIsBackedUpInsteadOfSilentlyOverwritten() throws IOException {
        plugin.getDimensionManager().setEnabled(ToggleDimension.NETHER, false);
        server.getPluginManager().disablePlugin(plugin);

        File dataFile = dataFile();
        Files.writeString(dataFile.toPath(), "dimensions:\n  nether: [unclosed\n");

        DimensionToggle reloaded = MockBukkit.load(DimensionToggle.class);
        reloaded.getUpdateChecker().stop();

        File[] backups = dataFile.getParentFile().listFiles((d, name) -> name.startsWith("data.yml.corrupt-"));
        assertEquals(1, backups == null ? 0 : backups.length);
        assertTrue(Files.readString(backups[0].toPath()).contains("[unclosed"));
        assertTrue(reloaded.isEnabled());
    }

    @Test
    void logLinesAreWrittenInTheOrderTheActionsHappened() throws IOException {
        for (int i = 0; i < 40; i++) {
            plugin.getLogManager().logByName("admin", "ACTION-" + i, null, null);
        }
        plugin.getIoExecutor().drain(5000);

        List<String> lines = Files.readAllLines(new File(plugin.getDataFolder(), "logs/dimensiontoggle.log").toPath());
        List<String> actions = lines.stream()
                .filter(line -> line.contains("ACTION-"))
                .map(line -> line.substring(line.indexOf("ACTION-"), line.indexOf(" |")))
                .toList();
        assertEquals(40, actions.size());
        for (int i = 0; i < 40; i++) {
            assertEquals("ACTION-" + i, actions.get(i));
        }
    }

    @Test
    void pendingWritesAreFlushedOnDisable() {
        plugin.getDimensionManager().setLocked(ToggleDimension.END, true, null);
        server.getPluginManager().disablePlugin(plugin);

        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(dataFile());
        assertTrue(onDisk.getBoolean("dimensions.end.locked"));
    }
}
