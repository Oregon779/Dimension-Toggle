package net.dimensiontoggle.command;

import be.seeseemelk.mockbukkit.command.ConsoleCommandSenderMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.dimensiontoggle.PluginTestBase;
import net.dimensiontoggle.model.ToggleDimension;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionToggleCommandTest extends PluginTestBase {

    private PlayerMock admin;
    private PlayerMock regular;

    @BeforeEach
    void addPlayers() {
        admin = addPlayer("Admin");
        admin.setOp(true);
        regular = addPlayer("Regular");
        regular.setOp(false);
        drain(admin);
        drain(regular);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    // The text a message key renders to (for keys without placeholders).
    private String expected(String key) {
        var messages = plugin.getMessageManager();
        return plain(messages.parse(messages.getPrefix() + messages.get(key)));
    }

    private static List<String> drain(PlayerMock player) {
        List<String> received = new ArrayList<>();
        for (Component c = player.nextComponentMessage(); c != null; c = player.nextComponentMessage()) {
            received.add(plain(c));
        }
        return received;
    }

    @Test
    void adminCommandsRequirePermission() {
        regular.performCommand("dt nether off");
        regular.performCommand("dt lockdown");
        regular.performCommand("dt limit end 1");

        assertTrue(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
        assertFalse(plugin.getDimensionManager().isLockdownActive());
        assertEquals(List.of(expected("no-permission"), expected("no-permission"), expected("no-permission")),
                drain(regular));
    }

    @Test
    void toggleNeedsAnExplicitState() {
        admin.performCommand("dt nether");
        admin.performCommand("dt nether maybe");
        assertTrue(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));

        admin.performCommand("dt nether off");
        assertFalse(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
        admin.performCommand("dt nether on");
        assertTrue(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
    }

    @Test
    void absurdMaintenanceDurationsAreRejectedNotCrashing() {
        admin.performCommand("dt maintenance nether 99999999999999h");
        admin.performCommand("dt maintenance nether 0");
        admin.performCommand("dt maintenance nether 5x");

        assertFalse(plugin.getMaintenanceManager().isPending(ToggleDimension.NETHER));
        assertEquals(List.of(expected("maintenance-invalid-duration"), expected("maintenance-invalid-duration"),
                expected("maintenance-invalid-duration")), drain(admin).stream()
                .filter(m -> m.equals(expected("maintenance-invalid-duration"))).toList());
    }

    @Test
    void maintenanceCanOnlyRunOncePerDimensionAndBeCancelled() {
        admin.performCommand("dt maintenance end 10m");
        assertTrue(plugin.getMaintenanceManager().isPending(ToggleDimension.END));
        assertEquals(600, plugin.getMaintenanceManager().getRemainingSeconds(ToggleDimension.END));

        admin.performCommand("dt maintenance end 1h");
        assertEquals(600, plugin.getMaintenanceManager().getRemainingSeconds(ToggleDimension.END));

        admin.performCommand("dt maintenance end cancel");
        assertFalse(plugin.getMaintenanceManager().isPending(ToggleDimension.END));
        assertTrue(plugin.getDimensionManager().isEnabled(ToggleDimension.END));
    }

    @Test
    void lockdownToggles() {
        admin.performCommand("dt lockdown");
        assertTrue(plugin.getDimensionManager().isLockdownActive());
        assertFalse(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
        assertFalse(plugin.getDimensionManager().isEnabled(ToggleDimension.END));

        admin.performCommand("dt lockdown");
        assertFalse(plugin.getDimensionManager().isLockdownActive());
        assertTrue(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
    }

    @Test
    void limitValidatesInputAndIsWrittenToConfigFile() {
        admin.performCommand("dt limit nether abc");
        admin.performCommand("dt limit nether -3");
        assertFalse(plugin.getConfigManager().getConfig().getBoolean("limits.nether.enabled"));

        admin.performCommand("dt limit nether 42");
        assertEquals(42, plugin.getConfigManager().getConfig().getInt("limits.nether.max-players"));

        plugin.getIoExecutor().drain(5000);
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "config.yml"));
        assertEquals(42, onDisk.getInt("limits.nether.max-players"));
        assertTrue(onDisk.getBoolean("limits.nether.enabled"));
        assertEquals(5, onDisk.getInt("limits.end.max-players"), "other dimension untouched");
    }

    @Test
    void consoleGetsAClearMessageForTheEditor() {
        ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) server.getConsoleSender();
        while (console.nextComponentMessage() != null) {
            // discard
        }
        server.dispatchCommand(console, "dt editor");

        assertEquals(expected("players-only"), plain(console.nextComponentMessage()));
    }

    @Test
    void tabCompletionHidesCommandsTheSenderCannotUse() {
        List<String> forAdmin = server.getCommandTabComplete(admin, "dt ");
        List<String> forRegular = server.getCommandTabComplete(regular, "dt ");

        assertTrue(forAdmin.contains("lockdown"));
        assertFalse(forRegular.contains("lockdown"));
        assertFalse(forRegular.contains("nether"));
    }

    @Test
    void reloadKeepsStateAndReappliesSettings() {
        admin.performCommand("dt nether off");
        admin.performCommand("dt reload");
        plugin.getUpdateChecker().stop();

        assertFalse(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
        assertTrue(drain(admin).contains(expected("reload-success")));
    }
}
