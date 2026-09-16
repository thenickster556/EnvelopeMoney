package com.example.envelopemoney;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MonthRolloverHelperTest {
    @Test
    public void prepareForLaunch_rollsForwardWithCarryOver() {
        Envelope gas = new Envelope("Gas", 100d);
        gas.setRemaining(35d);
        gas.setManualRemaining(null);
        gas.addTransaction(new Transaction("Gas", 65d, "2026-02-12", "Fuel"), "2026-02");

        List<Envelope> envelopes = new ArrayList<>();
        envelopes.add(gas);

        MonthRolloverHelper.Result result = MonthRolloverHelper.prepareForLaunch(envelopes, "2026-02", "2026-03", true);

        assertEquals("2026-03", result.getActiveMonth());
        assertTrue(result.rolledOver());
        Envelope rolledGas = result.getEnvelopes().get(0);
        assertEquals(100d, rolledGas.getLimit(), 0.001d);
        assertEquals(100d, rolledGas.getOriginalLimit(), 0.001d);
        assertEquals(135d, rolledGas.getRemaining(), 0.001d);
        assertEquals(135d, rolledGas.getMonthlyData("2026-03").limit, 0.001d);
    }

    @Test
    public void prepareForLaunch_repairsLegacyTransactionMonth() {
        Envelope personal = new Envelope("Personal", 200d);
        Transaction transaction = new Transaction("Personal", 10d, "2026-02-10", "Lunch");
        transaction.setMonth(null);
        personal.getTransactions().add(transaction);

        List<Envelope> envelopes = new ArrayList<>();
        envelopes.add(personal);

        MonthRolloverHelper.Result result = MonthRolloverHelper.prepareForLaunch(envelopes, "2026-02", "2026-02", true);

        assertEquals("2026-02", result.getEnvelopes().get(0).getTransactions().get(0).getMonth());
        assertEquals(190d, result.getEnvelopes().get(0).getMonthlyData("2026-02").remaining, 0.001d);
    }

    @Test
    public void prepareForLaunch_recoversMalformedNumericState() {
        Envelope outreach = new Envelope("Outreach", 80d);
        outreach.setOriginalLimit(Double.NaN);
        outreach.setRemaining(Double.NaN);
        outreach.setManualRemaining(Double.POSITIVE_INFINITY);

        List<Envelope> envelopes = new ArrayList<>();
        envelopes.add(outreach);

        MonthRolloverHelper.Result result = MonthRolloverHelper.prepareForLaunch(envelopes, "not-a-month", "2026-03", true);

        Envelope repaired = result.getEnvelopes().get(0);
        assertEquals("2026-03", result.getActiveMonth());
        assertTrue(Double.isFinite(repaired.getOriginalLimit()));
        assertTrue(Double.isFinite(repaired.getRemaining()));
        assertNotNull(repaired.getMonthlyData("2026-03"));
    }

    @Test
    public void prepareForLaunch_isIdempotentAfterSuccessfulRollover() {
        Envelope vacation = new Envelope("Vacation", 300d);
        vacation.setRemaining(250d);

        List<Envelope> envelopes = new ArrayList<>();
        envelopes.add(vacation);

        MonthRolloverHelper.Result first = MonthRolloverHelper.prepareForLaunch(envelopes, "2026-02", "2026-03", true);
        MonthRolloverHelper.Result second = MonthRolloverHelper.prepareForLaunch(first.getEnvelopes(), "2026-03", "2026-03", true);

        assertTrue(first.rolledOver());
        assertFalse(second.rolledOver());
        assertEquals(first.getEnvelopes().get(0).getMonthlyData("2026-03").limit,
                second.getEnvelopes().get(0).getMonthlyData("2026-03").limit,
                0.001d);
        assertEquals(300d, second.getEnvelopes().get(0).getLimit(), 0.001d);
    }
}
