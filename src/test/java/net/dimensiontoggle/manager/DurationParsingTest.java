package net.dimensiontoggle.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DurationParsingTest {

    private static Integer parse(String input) {
        return MaintenanceManager.parseDurationToSeconds(input);
    }

    @Test
    void plainNumberMeansMinutes() {
        assertEquals(600, parse("10"));
    }

    @Test
    void unitsCombine() {
        assertEquals(5400, parse("1h30m"));
        assertEquals(30, parse("30s"));
        assertEquals(3723, parse("1h2m3s"));
        assertEquals(300, parse("5M"));
    }

    @Test
    void rejectsGarbage() {
        assertNull(parse(null));
        assertNull(parse(""));
        assertNull(parse("   "));
        assertNull(parse("abc"));
        assertNull(parse("5x"));
        assertNull(parse("1h30"));
        assertNull(parse("h"));
        assertNull(parse("-5m"));
    }

    @Test
    void rejectsZero() {
        assertNull(parse("0"));
        assertNull(parse("0m"));
    }

    @Test
    void hugeValuesAreRejectedInsteadOfThrowingOrOverflowing() {
        // Used to throw an uncaught NumberFormatException (> Integer.MAX_VALUE)
        // straight out of the command handler.
        assertNull(parse("99999999999"));
        assertNull(parse("99999999999h"));
        // Used to silently overflow int arithmetic into a garbage value.
        assertNull(parse("999999999"));
        assertNull(parse("99999999h"));
    }

    @Test
    void acceptsEverythingThatFitsInAnIntNumberOfSeconds() {
        assertEquals(596523 * 3600, parse("596523h"));
        assertNull(parse("596524h"));
        assertEquals(35791394 * 60, parse("35791394"));
        assertNull(parse("35791395"));
    }
}
