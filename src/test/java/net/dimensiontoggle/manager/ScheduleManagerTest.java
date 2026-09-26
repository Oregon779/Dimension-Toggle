package net.dimensiontoggle.manager;

import net.dimensiontoggle.PluginTestBase;
import net.dimensiontoggle.model.ToggleDimension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleManagerTest extends PluginTestBase {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private ScheduleManager schedule;

    @BeforeEach
    void enableNetherSchedule() {
        schedule = plugin.getScheduleManager();
        var config = plugin.getConfigManager().getConfig();
        config.set("schedule.nether.enabled", true);
        config.set("schedule.nether.open-time", "08:00");
        config.set("schedule.nether.countdown-enabled", false);
    }

    private void tickAt(int hour, int minute, int second) {
        LocalTime time = LocalTime.of(hour, minute, second);
        schedule.setClock(Clock.fixed(LocalDate.of(2026, 1, 1).atTime(time).atZone(ZONE).toInstant(), ZONE));
        schedule.tick();
    }

    @Test
    void closesEvenWhenALagSpikeSkipsTheExactSecond() {
        plugin.getConfigManager().getConfig().set("schedule.nether.close-time", "22:00");

        tickAt(21, 59, 58);
        assertTrue(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));

        // 5 seconds pass between two ticks - 22:00:00 itself is never observed.
        tickAt(22, 0, 3);
        assertFalse(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
    }

    @Test
    void unquotedYamlTimeReadAsSexagesimalIntegerStillTriggers() {
        // What Bukkit's YAML parser actually hands back for `close-time: 22:00`.
        plugin.getConfigManager().getConfig().set("schedule.nether.close-time", 1320);

        tickAt(21, 59, 59);
        tickAt(22, 0, 0);
        assertFalse(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
    }

    @Test
    void doesNotFireOnTheVeryFirstTickAfterTheTargetAlreadyPassed() {
        plugin.getConfigManager().getConfig().set("schedule.nether.close-time", "22:00");

        // Server (re)started at 23:00 - no crossing was observed, so no action.
        tickAt(23, 0, 0);
        tickAt(23, 0, 1);
        assertTrue(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
    }

    @Test
    void scheduledOpeningDoesNotLiftAnActiveLockdown() {
        plugin.getConfigManager().getConfig().set("schedule.nether.close-time", "22:00");
        plugin.getDimensionManager().toggleLockdownWithBroadcast(null);
        assertFalse(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));

        tickAt(7, 59, 59);
        tickAt(8, 0, 0);

        assertFalse(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
        assertTrue(plugin.getDimensionManager().isLockdownActive());
    }

    @Test
    void scheduledOpeningWorksWithoutLockdown() {
        plugin.getConfigManager().getConfig().set("schedule.nether.close-time", "22:00");
        plugin.getDimensionManager().setEnabled(ToggleDimension.NETHER, false);

        tickAt(7, 59, 59);
        tickAt(8, 0, 0);

        assertTrue(plugin.getDimensionManager().isEnabled(ToggleDimension.NETHER));
    }
}
