package net.dimensiontoggle.manager;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScheduleTimeParsingTest {

    private static Object loadRaw(String yamlValue) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("close-time: " + yamlValue + "\n");
        return config.get("close-time");
    }

    @Test
    void unquotedTimesAreReadAsSexagesimalIntegersByBukkitYaml() throws Exception {
        // Documents the root cause: SnakeYAML (YAML 1.1) turns 22:00 into 1320.
        assertInstanceOf(Integer.class, loadRaw("22:00"));
        assertEquals(1320, loadRaw("22:00"));
    }

    @Test
    void unquotedTimesStillParseCorrectly() throws Exception {
        assertEquals(LocalTime.of(22, 0), ScheduleManager.parseTime(loadRaw("22:00")));
        assertEquals(LocalTime.of(22, 30), ScheduleManager.parseTime(loadRaw("22:30")));
        assertEquals(LocalTime.of(8, 0), ScheduleManager.parseTime(loadRaw("8:00")));
        assertEquals(LocalTime.of(8, 0), ScheduleManager.parseTime(loadRaw("08:00")));
        assertEquals(LocalTime.of(23, 59), ScheduleManager.parseTime(loadRaw("23:59")));
    }

    @Test
    void quotedTimesParse() throws Exception {
        assertEquals(LocalTime.of(22, 0), ScheduleManager.parseTime(loadRaw("\"22:00\"")));
        assertEquals(LocalTime.of(8, 0), ScheduleManager.parseTime(loadRaw("\"8:00\"")));
        assertEquals(LocalTime.of(0, 0), ScheduleManager.parseTime(loadRaw("\"00:00\"")));
        assertEquals(LocalTime.of(0, 30), ScheduleManager.parseTime(loadRaw("0:30")));
    }

    @Test
    void timesWithSecondsParse() throws Exception {
        assertEquals(LocalTime.of(22, 0, 30), ScheduleManager.parseTime(loadRaw("22:00:30")));
        assertEquals(LocalTime.of(1, 5), ScheduleManager.parseTime(loadRaw("1:05:00")));
    }

    @Test
    void twentyFourHundredMeansMidnightQuotedOrNot() throws Exception {
        assertEquals(LocalTime.MIDNIGHT, ScheduleManager.parseTime(loadRaw("\"24:00\"")));
        assertEquals(LocalTime.MIDNIGHT, ScheduleManager.parseTime(loadRaw("24:00")));
    }

    @Test
    void invalidValuesAreRejected() throws Exception {
        assertNull(ScheduleManager.parseTime(null));
        assertNull(ScheduleManager.parseTime(""));
        assertNull(ScheduleManager.parseTime("abc"));
        assertNull(ScheduleManager.parseTime(loadRaw("\"25:00\"")));
        assertNull(ScheduleManager.parseTime(loadRaw("25:00")));
        assertNull(ScheduleManager.parseTime(loadRaw("24:30")));
        assertNull(ScheduleManager.parseTime(loadRaw("\"8:0\"")));
        assertNull(ScheduleManager.parseTime(-5));
        assertNull(ScheduleManager.parseTime(86400));
        assertNull(ScheduleManager.parseTime(true));
    }

    @Test
    void formatTimeShowsHumanReadableValue() throws Exception {
        assertEquals("22:00", ScheduleManager.formatTime(loadRaw("22:00")));
        assertEquals("08:00", ScheduleManager.formatTime(loadRaw("8:00")));
        assertEquals("garbage", ScheduleManager.formatTime("garbage"));
        assertEquals("?", ScheduleManager.formatTime(null));
    }
}
