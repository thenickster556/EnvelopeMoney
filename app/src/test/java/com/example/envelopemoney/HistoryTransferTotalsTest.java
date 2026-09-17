package com.example.envelopemoney;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryTransferTotalsTest {

    @Test
    public void inboundMirrorIsExcludedFromHistoryTotal() {
        assertFalse(HistoryTransferTotals.includeAmountInHistoryTotal(true, false));
        assertTrue(HistoryTransferTotals.includeAmountInHistoryTotal(true, true));
        assertTrue(HistoryTransferTotals.includeAmountInHistoryTotal(false, false));
    }

    @Test
    public void spendingTotalIgnoresInboundAndKeepsOutgoingOutWhenTransfersVisible() {
        // $20 spend + $50 source transfer; $40 allocated out; inbound mirror not in spendGross.
        assertEquals(30.0d, HistoryTransferTotals.spendingTotal(70.0d, 40.0d, true), 0.0001d);
        assertEquals(70.0d, HistoryTransferTotals.spendingTotal(70.0d, 40.0d, false), 0.0001d);
    }

    @Test
    public void inboundPanelStaysNegativeWhenDestinationIsSelected() {
        double panel = 0d;
        panel = HistoryTransferTotals.addInboundToPanel(panel, 40.0d);
        panel = HistoryTransferTotals.addInboundToPanel(panel, 10.0d);
        assertEquals(-50.0d, panel, 0.0001d);
        assertEquals("To Savings: -$50.00", HistoryTransferTotals.formatToSummary("Savings", panel));
    }
}
