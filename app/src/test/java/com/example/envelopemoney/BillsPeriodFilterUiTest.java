package com.example.envelopemoney;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillsPeriodFilterUiTest {

    @Test
    public void selectingBillsFilterTurnsTransfersOn() {
        assertTrue(BillsPeriodFilterUi.transfersVisibleAfterBillsFilterChange(true, false));
        assertTrue(BillsPeriodFilterUi.transfersVisibleAfterBillsFilterChange(true, true));
    }

    @Test
    public void clearingBillsFilterLeavesTransferToggleUnchanged() {
        assertFalse(BillsPeriodFilterUi.transfersVisibleAfterBillsFilterChange(false, false));
        assertTrue(BillsPeriodFilterUi.transfersVisibleAfterBillsFilterChange(false, true));
    }
}
