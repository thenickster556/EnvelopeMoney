package com.example.envelopemoney;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpendAnalysisHelperTest {

    @Test
    public void monthKeys_last3FromAugust() {
        List<String> keys = SpendAnalysisHelper.monthKeys("2026-08", 3);
        assertEquals(Arrays.asList("2026-06", "2026-07", "2026-08"), keys);
    }

    @Test
    public void monthKeys_last12WrapsYear() {
        List<String> keys = SpendAnalysisHelper.monthKeys("2026-02", 12);
        assertEquals(12, keys.size());
        assertEquals("2025-03", keys.get(0));
        assertEquals("2026-02", keys.get(11));
    }

    @Test
    public void monthKeys_invalidLastNDefaultsTo3() {
        assertEquals(3, SpendAnalysisHelper.monthKeys("2026-08", 4).size());
        assertEquals(3, SpendAnalysisHelper.normalizeLastN(0));
        assertEquals(6, SpendAnalysisHelper.normalizeLastN(6));
        assertEquals(12, SpendAnalysisHelper.normalizeLastN(12));
    }

    @Test
    public void countsAsSpend_excludesTransferSourceAndMirrorsWhenOff() {
        Transaction spending = tx("Groceries", 42d, "2026-08-03", null, null, null);
        Transaction source = tx("Fun", 100d, "2026-08-01", "tid", null, null);
        Transaction mirror = tx("Savings", -40d, "2026-08-01", "tid", "b1", null);
        Transaction split = tx("Gas", 12d, "2026-08-02", null, null, "split-1");

        assertTrue(SpendAnalysisHelper.countsAsSpend(spending, false));
        assertFalse(SpendAnalysisHelper.countsAsSpend(source, false));
        assertFalse(SpendAnalysisHelper.countsAsSpend(mirror, false));
        assertTrue(SpendAnalysisHelper.countsAsSpend(split, false));

        assertTrue(SpendAnalysisHelper.countsAsSpend(source, true));
        assertTrue(SpendAnalysisHelper.countsAsSpend(mirror, true));
    }

    @Test
    public void analyze_excludesTransfersByDefaultAndAlwaysCountsSplits() {
        Envelope groceries = pond("Groceries", 500d,
                tx("Groceries", 80d, "2026-06-02", null, null, null),
                tx("Groceries", 90d, "2026-07-02", null, null, null),
                tx("Groceries", 100d, "2026-08-02", null, null, null));
        Envelope fun = pond("Fun", 250d,
                tx("Fun", 40d, "2026-08-01", "tid", null, null));
        Envelope savings = pond("Savings", 300d,
                tx("Savings", -40d, "2026-08-01", "tid", "b1", null));
        Envelope gas = pond("Gas", 200d,
                tx("Gas", 15d, "2026-08-04", null, null, "split-1"));

        SpendAnalysisHelper.SpendAnalysisResult result = SpendAnalysisHelper.analyze(
                Arrays.asList(groceries, fun, savings, gas),
                query("2026-08", 3, Collections.emptyList(), false));

        assertEquals(3, result.months.size());
        assertEquals("2026-06", result.months.get(0).month);
        assertEquals(80d, result.months.get(0).totalSpend, 0.0001d);
        assertEquals(90d, result.months.get(1).totalSpend, 0.0001d);
        assertEquals(115d, result.months.get(2).totalSpend, 0.0001d);
        assertEquals(1250d, result.months.get(2).totalLimit, 0.0001d);
    }

    @Test
    public void analyze_includeTransfersMatchesAllAmounts() {
        Envelope fun = pond("Fun", 250d,
                tx("Fun", 50d, "2026-08-02", null, null, null),
                tx("Fun", 100d, "2026-08-01", "tid", null, null));
        Envelope savings = pond("Savings", 300d,
                tx("Savings", -40d, "2026-08-01", "tid", "b1", null));

        SpendAnalysisHelper.SpendAnalysisResult off = SpendAnalysisHelper.analyze(
                Arrays.asList(fun, savings),
                query("2026-08", 3, Collections.emptyList(), false));
        SpendAnalysisHelper.SpendAnalysisResult on = SpendAnalysisHelper.analyze(
                Arrays.asList(fun, savings),
                query("2026-08", 3, Collections.emptyList(), true));

        assertEquals(50d, off.months.get(2).totalSpend, 0.0001d);
        assertEquals(110d, on.months.get(2).totalSpend, 0.0001d);
        assertEquals(50d, findPond(off.byPond, "Fun").spend, 0.0001d);
        assertEquals(150d, findPond(on.byPond, "Fun").spend, 0.0001d);
        assertEquals(-40d, findPond(on.byPond, "Savings").spend, 0.0001d);
    }

    @Test
    public void analyze_overBudgetOnlyWhenSpendExceedsLimit() {
        Envelope gas = pond("Gas", 200d,
                tx("Gas", 200d, "2026-07-01", null, null, null),
                tx("Gas", 210d, "2026-08-01", null, null, null));
        Envelope groceries = pond("Groceries", 500d,
                tx("Groceries", 500d, "2026-08-01", null, null, null));

        SpendAnalysisHelper.SpendAnalysisResult result = SpendAnalysisHelper.analyze(
                Arrays.asList(gas, groceries),
                query("2026-08", 3, Collections.emptyList(), false));

        assertEquals(1, result.overBudget.size());
        SpendAnalysisHelper.OverBudgetRow row = result.overBudget.get(0);
        assertEquals("2026-08", row.month);
        assertEquals("Gas", row.pondName);
        assertEquals(210d, row.spend, 0.0001d);
        assertEquals(200d, row.limit, 0.0001d);
        assertEquals(10d, row.overBy, 0.0001d);
    }

    @Test
    public void analyze_emptyPondsStillEmitZeroMonths() {
        Envelope empty = pond("Fun", 250d);
        SpendAnalysisHelper.SpendAnalysisResult result = SpendAnalysisHelper.analyze(
                Collections.singletonList(empty),
                query("2026-08", 3, Collections.emptyList(), false));

        assertEquals(3, result.months.size());
        assertEquals(0d, result.months.get(0).totalSpend, 0.0001d);
        assertEquals(0d, result.months.get(1).totalSpend, 0.0001d);
        assertEquals(0d, result.months.get(2).totalSpend, 0.0001d);
        assertEquals(250d, result.months.get(0).totalLimit, 0.0001d);
        assertTrue(result.overBudget.isEmpty());
        assertEquals(0d, result.byPond.get(0).spend, 0.0001d);
        assertFalse(result.hasSpendInRange);
    }

    @Test
    public void analyze_pondFilterUsesCurrentEnvelopeName() {
        Envelope groceries = pond("Groceries", 500d,
                tx("Groceries", 40d, "2026-08-01", null, null, null));
        Envelope gas = pond("Gas", 200d,
                tx("Gas", 90d, "2026-08-01", null, null, null));

        SpendAnalysisHelper.SpendAnalysisResult result = SpendAnalysisHelper.analyze(
                Arrays.asList(groceries, gas),
                query("2026-08", 3, Collections.singletonList("Groceries"), false));

        assertEquals(40d, result.months.get(2).totalSpend, 0.0001d);
        assertEquals(500d, result.months.get(2).totalLimit, 0.0001d);
        assertEquals(1, result.byPond.size());
        assertEquals("Groceries", result.byPond.get(0).pondName);
        assertEquals(1, result.thisMonth.size());
        assertEquals("Groceries", result.thisMonth.get(0).pondName);
    }

    @Test
    public void analyze_overBudgetSortedByOverByThenMonth() {
        Envelope gas = pond("Gas", 100d,
                tx("Gas", 130d, "2026-07-01", null, null, null),
                tx("Gas", 110d, "2026-08-01", null, null, null));
        Envelope fun = pond("Fun", 50d,
                tx("Fun", 80d, "2026-08-01", null, null, null));

        SpendAnalysisHelper.SpendAnalysisResult result = SpendAnalysisHelper.analyze(
                Arrays.asList(gas, fun),
                query("2026-08", 3, Collections.emptyList(), false));

        assertEquals(3, result.overBudget.size());
        assertEquals("Fun", result.overBudget.get(0).pondName);
        assertEquals(30d, result.overBudget.get(0).overBy, 0.0001d);
        assertEquals("Gas", result.overBudget.get(1).pondName);
        assertEquals("2026-07", result.overBudget.get(1).month);
        assertEquals(30d, result.overBudget.get(1).overBy, 0.0001d);
        assertEquals("Gas", result.overBudget.get(2).pondName);
        assertEquals("2026-08", result.overBudget.get(2).month);
    }

    @Test
    public void snapshot_usesEnvelopeRemainingAndLimitNotMonthData() {
        Envelope gas = pond("Gas", 200d, tx("Gas", 210d, "2026-08-01", null, null, null));
        gas.getMonthlyData("2026-08").limit = 999d;
        gas.setRemaining(-10d);

        SpendAnalysisHelper.SpendAnalysisResult result = SpendAnalysisHelper.analyze(
                Collections.singletonList(gas),
                query("2026-08", 3, Collections.emptyList(), false));

        SpendAnalysisHelper.SnapshotRow snap = result.thisMonth.get(0);
        assertEquals(210d, snap.spend, 0.0001d);
        assertEquals(200d, snap.limit, 0.0001d);
        assertEquals(-10d, snap.remaining, 0.0001d);
        assertTrue(snap.overBudget);
    }

    @Test
    public void barScaleMax_neverZero() {
        assertEquals(0.01d, SpendAnalysisHelper.barScaleMax(Collections.singletonList(0d)), 0.0001d);
        assertEquals(12.34d, SpendAnalysisHelper.barScaleMax(Arrays.asList(1d, 12.34d, 0d)), 0.0001d);
    }

    private static SpendAnalysisHelper.SpendAnalysisQuery query(
            String endMonth, int lastN, List<String> pondNames, boolean includeTransfers) {
        return new SpendAnalysisHelper.SpendAnalysisQuery(endMonth, lastN, pondNames, includeTransfers);
    }

    private static Envelope pond(String name, double limit, Transaction... transactions) {
        Envelope envelope = new Envelope(name, limit);
        envelope.getTransactions().addAll(Arrays.asList(transactions));
        return envelope;
    }

    private static Transaction tx(String pond, double amount, String date,
                                  String transferId, String transferBucketId, String splitGroupId) {
        Transaction transaction = new Transaction(pond, amount, date, "note");
        transaction.setTransferId(transferId);
        transaction.setTransferBucketId(transferBucketId);
        transaction.setSplitPurchaseGroupId(splitGroupId);
        return transaction;
    }

    private static SpendAnalysisHelper.PondSpend findPond(
            List<SpendAnalysisHelper.PondSpend> rows, String name) {
        for (SpendAnalysisHelper.PondSpend row : rows) {
            if (name.equals(row.pondName)) {
                return row;
            }
        }
        throw new AssertionError("missing pond " + name);
    }
}
