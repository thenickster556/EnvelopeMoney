package com.example.envelopemoney;

import java.util.Locale;

/**
 * History Total vs transfers-panel math. Inbound mirrors (money put into a destination pond)
 * stay out of Total and appear as a negative To line so the deposited amount is visible.
 */
public final class HistoryTransferTotals {

    private HistoryTransferTotals() {
    }

    /**
     * Source transfer rows and ordinary spending count in Total. Destination mirror rows do not.
     */
    public static boolean includeAmountInHistoryTotal(boolean isTransfer, boolean isSourceSide) {
        return !isTransfer || isSourceSide;
    }

    /**
     * {@code spendGross} is the sum of rows that {@link #includeAmountInHistoryTotal} kept.
     * When transfers are visible, allocated outgoing is also removed from Total (it is not spend).
     */
    public static double spendingTotal(double spendGross, double outgoingAllocated,
                                       boolean subtractOutgoing) {
        double total = MoneyMath.roundToCents(spendGross);
        if (subtractOutgoing) {
            total = MoneyMath.roundToCents(total - outgoingAllocated);
        }
        return total;
    }

    /** Accumulate money transferred into a destination; always negative, never zeroed. */
    public static double addInboundToPanel(double currentPanelTotal, double allocationAmount) {
        return MoneyMath.roundToCents(currentPanelTotal - Math.abs(allocationAmount));
    }

    public static String formatSignedMoney(double amount) {
        double cents = MoneyMath.roundToCents(amount);
        String sign = cents < 0 ? "-" : "";
        return sign + String.format(Locale.US, "$%.2f", Math.abs(cents));
    }

    public static String formatToSummary(String envelopeName, double inboundTotal) {
        String name = envelopeName != null ? envelopeName : "";
        return "To " + name + ": " + formatSignedMoney(inboundTotal);
    }
}
