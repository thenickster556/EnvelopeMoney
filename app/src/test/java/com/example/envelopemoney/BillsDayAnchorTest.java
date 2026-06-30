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
    public void singleDay_todayAfterBillsDay_usesCurrentMonth() {
        List<Integer> days = Collections.singletonList(10);
        Calendar today = cal(2026, Calendar.MAY, 15);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-05-10", fmt(anchor));
    }

    @Test
    public void singleDay_todayBeforeBillsDay_walksPriorMonth() {
        List<Integer> days = Collections.singletonList(15);
        Calendar today = cal(2026, Calendar.FEBRUARY, 10);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-01-15", fmt(anchor));
    }

    @Test
    public void multiDay_todayAfterFirstBillsDay() {
        List<Integer> days = Arrays.asList(10, 25);
        Calendar today = cal(2026, Calendar.APRIL, 20);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-04-10", fmt(anchor));
    }

    @Test
    public void multiDay_noDayYetInMonth() {
        List<Integer> days = Arrays.asList(10, 25);
        Calendar today = cal(2026, Calendar.APRIL, 5);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-03-25", fmt(anchor));
    }

    @Test
    public void multiDay_todayOnPeriodEnd_usesPriorBillsDayInSet() {
        List<Integer> days = Arrays.asList(1, 15);
        Calendar today = cal(2026, Calendar.APRIL, 15);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-04-01", fmt(anchor));
    }

    @Test
    public void singleDay_todayOnBillsDay_usesPreviousMonth() {
        List<Integer> days = Collections.singletonList(17);
        Calendar today = cal(2026, Calendar.APRIL, 17);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-03-17", fmt(anchor));
    }

    @Test
    public void february_clamps31st() {
        List<Integer> days = Collections.singletonList(31);
        Calendar today = cal(2026, Calendar.FEBRUARY, 15);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-01-31", fmt(anchor));
    }

    @Test
    public void dom31_clampsInShorterMonth() {
        List<Integer> days = Collections.singletonList(31);
        Calendar today = cal(2026, Calendar.MARCH, 31);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-02-28", fmt(anchor));
    }

    @Test
    public void singleDay_dayAfterBillsDom_usesCurrentMonthNotPrevious() {
        List<Integer> days = Collections.singletonList(12);
        Calendar today = cal(2026, Calendar.MAY, 13);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-05-12", fmt(anchor));
    }

    @Test
    public void singleDay_dayBeforeBillsDomInMonth_walksPriorMonth() {
        List<Integer> days = Collections.singletonList(12);
        Calendar today = cal(2026, Calendar.MAY, 5);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-04-12", fmt(anchor));
    }

    @Test
    public void singleDay_laterInMonth_usesCurrentMonthAnchor() {
        List<Integer> days = Collections.singletonList(17);
        Calendar today = cal(2026, Calendar.APRIL, 20);
        Date anchor = BillsDayAnchor.computeAnchorDate(today, days);
        assertEquals("2026-04-17", fmt(anchor));
    }
}
