package com.example.envelopemoney;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cent-precision helpers for bank reconciliation display and allocation defaults. */
public final class MoneyMath {

    private static final int CENTS_SCALE = 2;
    private static final int PERCENT_TOTAL = 100;

    private MoneyMath() {
    }

    public static double roundToCents(double value) {
        return BigDecimal.valueOf(value)
                .setScale(CENTS_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Integer percent weights that sum to 100. First share uses ceiling(100/n); remaining shares
     * split the rest evenly (e.g. n=3 → 34, 33, 33).
     */
    public static int[] splitIntegerPercentsFirstCeiling(int parts) {
        if (parts <= 0) {
            return new int[0];
        }
        if (parts == 1) {
            return new int[]{PERCENT_TOTAL};
        }
        int[] percents = new int[parts];
        percents[0] = (PERCENT_TOTAL + parts - 1) / parts;
        int eachOther = (PERCENT_TOTAL - percents[0]) / (parts - 1);
        for (int i = 1; i < parts; i++) {
            percents[i] = eachOther;
        }
        return percents;
    }

    /**
     * Dollar amounts from {@code total} and percent weights. Cents always sum to the total; the last
     * bucket absorbs rounding remainder.
     */
    public static double[] splitTotalByPercents(double total, int[] percents) {
        if (percents == null || percents.length == 0) {
            return new double[0];
        }
        double safeTotal = roundToCents(Math.max(0d, total));
        long totalCents = Math.round(safeTotal * 100d);
        double[] amounts = new double[percents.length];
        long allocatedCents = 0L;
        for (int i = 0; i < percents.length; i++) {
            if (i == percents.length - 1) {
                amounts[i] = roundToCents((totalCents - allocatedCents) / 100d);
            } else {
                long bucketCents = (totalCents * percents[i]) / PERCENT_TOTAL;
                amounts[i] = roundToCents(bucketCents / 100d);
                allocatedCents += bucketCents;
            }
        }
        return amounts;
    }
}
