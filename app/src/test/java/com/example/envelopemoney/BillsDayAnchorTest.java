package com.example.envelopemoney;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BillsDayAnchorTest {

    private static Calendar cal(int y, int m0, int d) {
        Calendar c = Calendar.getInstance(Locale.US);
        c.clear();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, m0);
        c.set(Calendar.DAY_OF_MONTH, d);
        return c;
    }

    private static String fmt(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    @Test
    public void emptyOrNull_returnsNull() {
        assertNull(BillsDayAnchor.computeAnchorDate(Calendar.getInstance(), null));
        assertNull(BillsDayAnchor.computeAnchorDate(Calendar.getInstance(), Collections.emptyList()));
    }

    @Test
    public void sameMonth_picksLatestBillsDayOnOrBeforeToday() {
        List<Integer> days = Arrays.asList(10, 25);
        Calendar today = cal(2026, Calendar.APRIL, 20);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-04-10", fmt(anchor));
    }

    @Test
    public void noDayYetInMonth_walksToPreviousMonth() {
        List<Integer> days = Arrays.asList(10, 25);
        Calendar today = cal(2026, Calendar.APRIL, 5);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-03-25", fmt(anchor));
    }

    @Test
    public void february_clamps31stToLastDay() {
        List<Integer> days = Collections.singletonList(31);
        Calendar today = cal(2026, Calendar.FEBRUARY, 15);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-01-31", fmt(anchor));
    }

    @Test
    public void todayIsBillsDay_includesToday() {
        List<Integer> days = Arrays.asList(1, 15);
        Calendar today = cal(2026, Calendar.APRIL, 15);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-04-15", fmt(anchor));
    }
}
