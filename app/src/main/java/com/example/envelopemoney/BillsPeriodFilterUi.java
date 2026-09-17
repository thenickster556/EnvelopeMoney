package com.example.envelopemoney;

/**
 * UI rules for the history bills-period filter. Isolated so the transfer auto-on
 * behavior can be tested without MainActivity.
 */
public final class BillsPeriodFilterUi {

    private BillsPeriodFilterUi() {
    }

    /**
     * Selecting the bills-period filter shows transfers so the bills window includes
     * pond-to-pond moves. Clearing the filter leaves the transfer toggle unchanged.
     */
    public static boolean transfersVisibleAfterBillsFilterChange(
            boolean billsFilterActive,
            boolean transfersCurrentlyVisible) {
        if (billsFilterActive) {
            return true;
        }
        return transfersCurrentlyVisible;
    }
}
