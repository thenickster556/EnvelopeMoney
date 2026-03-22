package com.example.envelopemoney;

import org.junit.Test;

import static org.junit.Assert.*;

public class MonthTrackerTest {
    @Test
    public void normalizeMonth_acceptsYearMonthShape() {
        assertEquals("2026-03", MonthTracker.normalizeMonth("2026-03"));
    }

    @Test
    public void normalizeMonth_rejectsMalformedMonth() {
        assertNull(MonthTracker.normalizeMonth("03/2026"));
        assertNull(MonthTracker.normalizeMonth("2026-3"));
        assertNull(MonthTracker.normalizeMonth(""));
    }

    @Test
    public void shouldRollover_handlesMissingStoredMonth() {
        assertTrue(MonthTracker.shouldRollover(null, "2026-03"));
    }

    @Test
    public void shouldRollover_detectsSameMonth() {
        assertFalse(MonthTracker.shouldRollover("2026-03", "2026-03"));
    }
}
