package com.example.envelopemoney;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PondBankReconciliationHelperTest {

    private static Calendar cal(int y, int m0, int d) {
        Calendar c = Calendar.getInstance(Locale.US);
        c.clear();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, m0);
        c.set(Calendar.DAY_OF_MONTH, d);
        return c;
    }

    private static Calendar monthStart(int y, int m0) {
        return cal(y, m0, 1);
    }

    @Test
    public void userExample_scheduleGapStillToDeposit() {
        List<Integer> paydays = Arrays.asList(1, 15);
        Calendar may = monthStart(2026, Calendar.MAY);
        Calendar today = cal(2026, Calendar.MAY, 13);
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 50d, paydays, may, today);
        assertTrue(r.isActive());
        assertEquals(100.00, r.getMonthTarget(), 0.001);
        assertEquals(50.00, r.getInBank(), 0.001);
        assertEquals(0.00, r.getStillToDepositForMonth(), 0.001);
        assertEquals(50.00, r.getFullMonthStillToDeposit(), 0.001);
        assertEquals(50.00, r.getPerPayday(), 0.001);
        assertEquals(2, r.getPaydaysInMonth());
        assertEquals(1, r.getPaydaysPassed());
    }

    @Test
    public void nullAccount_reconciliationInactive() {
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, null, Arrays.asList(1, 15),
                        monthStart(2026, Calendar.MAY), cal(2026, Calendar.MAY, 13));
        assertFalse(r.isActive());
    }

    @Test
    public void accountOverLimit_stillToDepositZero_aheadAmount() {
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 120d, Arrays.asList(1),
                        monthStart(2026, Calendar.MAY), cal(2026, Calendar.MAY, 20));
        assertEquals(0.00, r.getStillToDepositForMonth(), 0.001);
        assertEquals(20.00, r.getAheadOfTarget(), 0.001);
    }

    @Test
    public void day31InFebruary_clampsToLastDay() {
        List<Integer> days = Collections.singletonList(31);
        Calendar feb = monthStart(2026, Calendar.FEBRUARY);
        List<Integer> resolved = PondBankReconciliationHelper.resolvePaydayDaysInMonth(days, feb);
        assertEquals(1, resolved.size());
        assertEquals(28, (int) resolved.get(0));
    }

    @Test
    public void midMonthSchedule_stillToDepositEqualsBehindSchedule() {
        List<Integer> paydays = Arrays.asList(1, 15);
        Calendar may = monthStart(2026, Calendar.MAY);
        Calendar today = cal(2026, Calendar.MAY, 13);
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 40d, paydays, may, today);
        assertEquals(50.00, r.getPerPayday(), 0.001);
        assertEquals(1, r.getPaydaysPassed());
        assertEquals(50.00, r.getExpectedInBankByToday(), 0.001);
        assertEquals(10.00, r.getStillToDepositForMonth(), 0.001);
        assertEquals(10.00, r.getBehindSchedule(), 0.001);
    }

    @Test
    public void beforeFirstPayday_stillToDepositZero() {
        List<Integer> paydays = Arrays.asList(15, 30);
        Calendar may = monthStart(2026, Calendar.MAY);
        Calendar today = cal(2026, Calendar.MAY, 10);
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 0d, paydays, may, today);
        assertEquals(0, r.getPaydaysPassed());
        assertEquals(0.00, r.getStillToDepositForMonth(), 0.001);
    }

    @Test
    public void duplicateClampedPaydays_areDeduped() {
        List<Integer> days = Arrays.asList(31, 31);
        Calendar feb = monthStart(2026, Calendar.FEBRUARY);
        List<Integer> resolved = PondBankReconciliationHelper.resolvePaydayDaysInMonth(days, feb);
        assertEquals(1, resolved.size());
    }

    @Test
    public void compute_neverReturnsMoreThanTwoDecimalPlaces() {
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 33.33d, Arrays.asList(1, 15, 30),
                        monthStart(2026, Calendar.MAY), cal(2026, Calendar.MAY, 20));
        assertTwoDecimals(r.getStillToDepositForMonth());
        assertTwoDecimals(r.getPerPayday());
        assertTwoDecimals(r.getExpectedInBankByToday());
    }

    private static void assertTwoDecimals(double value) {
        long cents = Math.round(value * 100);
        assertEquals(value, cents / 100.0, 0.00001);
    }
}
