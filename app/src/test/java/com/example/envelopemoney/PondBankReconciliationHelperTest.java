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

/**
 * New formula (must fail against legacy schedule-gap Remaining):
 * stillToDeposit = unpassed payday slices of Limit;
 * estimatedRemaining = Account + unlocked − monthSpend.
 */
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
    public void beforeFirstPayday_stillEqualsLimit_remainingIsAccountMinusSpend() {
        List<Integer> paydays = Arrays.asList(15, 30);
        Calendar may = monthStart(2026, Calendar.MAY);
        Calendar today = cal(2026, Calendar.MAY, 10);
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 40d, paydays, may, today, 5d);
        assertTrue(r.isActive());
        assertEquals(0, r.getPaydaysPassed());
        assertEquals(100.00, r.getStillToDepositForMonth(), 0.001);
        assertEquals(0.00, r.getUnlockedFromPaydays(), 0.001);
        assertEquals(35.00, r.getEstimatedRemaining(), 0.001);
        assertEquals(100.00, r.getMonthTarget(), 0.001);
        assertEquals(40.00, r.getInBank(), 0.001);
    }

    @Test
    public void afterFirstOfTwo_unlockedHalf_stillHalf_remainingAccountPlusHalfMinusSpend() {
        List<Integer> paydays = Arrays.asList(1, 15);
        Calendar may = monthStart(2026, Calendar.MAY);
        Calendar today = cal(2026, Calendar.MAY, 13);
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 40d, paydays, may, today, 20d);
        assertTrue(r.isActive());
        assertEquals(1, r.getPaydaysPassed());
        assertEquals(2, r.getPaydaysInMonth());
        assertEquals(50.00, r.getPerPayday(), 0.001);
        assertEquals(50.00, r.getUnlockedFromPaydays(), 0.001);
        assertEquals(50.00, r.getStillToDepositForMonth(), 0.001);
        assertEquals(70.00, r.getEstimatedRemaining(), 0.001);
        assertEquals(50.00, r.getExpectedInBankByToday(), 0.001);
    }

    @Test
    public void afterAllPaydays_stillZero_remainingAccountPlusLimitMinusSpend() {
        List<Integer> paydays = Arrays.asList(1, 15);
        Calendar may = monthStart(2026, Calendar.MAY);
        Calendar today = cal(2026, Calendar.MAY, 20);
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 40d, paydays, may, today, 20d);
        assertEquals(2, r.getPaydaysPassed());
        assertEquals(0.00, r.getStillToDepositForMonth(), 0.001);
        assertEquals(100.00, r.getUnlockedFromPaydays(), 0.001);
        assertEquals(120.00, r.getEstimatedRemaining(), 0.001);
    }

    @Test
    public void pastMonth_allPaydaysPassed_stillZero() {
        List<Integer> paydays = Arrays.asList(1, 15);
        Calendar april = monthStart(2026, Calendar.APRIL);
        Calendar today = cal(2026, Calendar.MAY, 10);
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 40d, paydays, april, today, 10d);
        assertEquals(2, r.getPaydaysPassed());
        assertEquals(0.00, r.getStillToDepositForMonth(), 0.001);
        assertEquals(100.00, r.getUnlockedFromPaydays(), 0.001);
        assertEquals(130.00, r.getEstimatedRemaining(), 0.001);
    }

    @Test
    public void futureMonth_noPaydaysPassed_stillEqualsLimit() {
        List<Integer> paydays = Arrays.asList(1, 15);
        Calendar june = monthStart(2026, Calendar.JUNE);
        Calendar today = cal(2026, Calendar.MAY, 10);
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 40d, paydays, june, today, 0d);
        assertEquals(0, r.getPaydaysPassed());
        assertEquals(100.00, r.getStillToDepositForMonth(), 0.001);
        assertEquals(40.00, r.getEstimatedRemaining(), 0.001);
    }

    @Test
    public void spendingChangesRemaining_limitUnchanged() {
        List<Integer> paydays = Arrays.asList(1, 15);
        Calendar may = monthStart(2026, Calendar.MAY);
        Calendar today = cal(2026, Calendar.MAY, 13);
        PondBankReconciliationHelper.Result lowSpend =
                PondBankReconciliationHelper.compute(100d, 40d, paydays, may, today, 5d);
        PondBankReconciliationHelper.Result highSpend =
                PondBankReconciliationHelper.compute(100d, 40d, paydays, may, today, 25d);
        assertEquals(100.00, lowSpend.getMonthTarget(), 0.001);
        assertEquals(100.00, highSpend.getMonthTarget(), 0.001);
        assertEquals(85.00, lowSpend.getEstimatedRemaining(), 0.001);
        assertEquals(65.00, highSpend.getEstimatedRemaining(), 0.001);
        assertEquals(50.00, lowSpend.getStillToDepositForMonth(), 0.001);
        assertEquals(50.00, highSpend.getStillToDepositForMonth(), 0.001);
    }

    @Test
    public void paydaySharesSumToLimit() {
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 0d, Arrays.asList(1, 15, 30),
                        monthStart(2026, Calendar.MAY), cal(2026, Calendar.MAY, 20), 0d);
        assertEquals(3, r.getPaydaysInMonth());
        double unlockedPlusStill = MoneyMath.roundToCents(
                r.getUnlockedFromPaydays() + r.getStillToDepositForMonth());
        assertEquals(100.00, unlockedPlusStill, 0.001);
        assertEquals(100.00, r.getMonthTarget(), 0.001);
    }

    @Test
    public void nullAccount_reconciliationInactive() {
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, null, Arrays.asList(1, 15),
                        monthStart(2026, Calendar.MAY), cal(2026, Calendar.MAY, 13), 0d);
        assertFalse(r.isActive());
        assertEquals(0.00, r.getEstimatedRemaining(), 0.001);
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
                        monthStart(2026, Calendar.MAY), cal(2026, Calendar.MAY, 20), 12.345d);
        assertTwoDecimals(r.getStillToDepositForMonth());
        assertTwoDecimals(r.getPerPayday());
        assertTwoDecimals(r.getExpectedInBankByToday());
        assertTwoDecimals(r.getEstimatedRemaining());
        assertTwoDecimals(r.getUnlockedFromPaydays());
    }

    @Test
    public void negativeRemaining_whenOverspendAllowed() {
        List<Integer> paydays = Arrays.asList(1);
        Calendar may = monthStart(2026, Calendar.MAY);
        Calendar today = cal(2026, Calendar.MAY, 20);
        PondBankReconciliationHelper.Result r =
                PondBankReconciliationHelper.compute(100d, 10d, paydays, may, today, 150d);
        assertEquals(1, r.getPaydaysPassed());
        assertEquals(0.00, r.getStillToDepositForMonth(), 0.001);
        assertEquals(-40.00, r.getEstimatedRemaining(), 0.001);
    }

    private static void assertTwoDecimals(double value) {
        long cents = Math.round(value * 100);
        assertEquals(value, cents / 100.0, 0.00001);
    }
}
