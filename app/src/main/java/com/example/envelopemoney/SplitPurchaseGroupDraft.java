package com.example.envelopemoney;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validation and totals for multi-pond split purchases (sum of slices must equal purchase total).
 */
public final class SplitPurchaseGroupDraft {
    private SplitPurchaseGroupDraft() {
    }

    public static double allocatedTotal(List<SplitPurchaseSliceAllocation> slices) {
        double total = 0d;
        if (slices == null) {
            return total;
        }
        for (SplitPurchaseSliceAllocation s : slices) {
            if (s == null) {
                continue;
            }
            total += safe(s.getAmount());
        }
        return total;
    }

    public static TransferGroupValidationResult validate(double purchaseTotal, List<SplitPurchaseSliceAllocation> slices) {
        if (slices == null || slices.size() < 2) {
            return TransferGroupValidationResult.invalid("Add at least two split slices");
        }
        Set<String> ponds = new HashSet<>();
        double sum = 0d;
        for (SplitPurchaseSliceAllocation s : slices) {
            if (s == null) {
                return TransferGroupValidationResult.invalid("Split slice is missing");
            }
            String pond = normalize(s.getPondName());
            if (pond.isEmpty()) {
                return TransferGroupValidationResult.invalid("Choose a pond for every slice");
            }
            if (!ponds.add(pond.toLowerCase(Locale.US))) {
                return TransferGroupValidationResult.invalid("Each pond can only appear once in a split");
            }
            if (s.getAmount() <= 0d) {
                return TransferGroupValidationResult.invalid("Every slice must be greater than $0.00");
            }
            sum += s.getAmount();
        }
        if (Math.abs(sum - purchaseTotal) > 0.01d) {
            return TransferGroupValidationResult.invalid(String.format(Locale.getDefault(),
                    "Slices must sum to the purchase total (off by $%.2f)", sum - purchaseTotal));
        }
        return TransferGroupValidationResult.valid();
    }

    private static double safe(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0d;
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
