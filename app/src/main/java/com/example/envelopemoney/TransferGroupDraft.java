package com.example.envelopemoney;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure helper for grouped transfer validation and summary math.
 */
public final class TransferGroupDraft {
    private TransferGroupDraft() {
    }

    public static double allocatedTotal(List<TransferBucketAllocation> allocations) {
        double total = 0d;
        if (allocations == null) {
            return total;
        }
        for (TransferBucketAllocation allocation : allocations) {
            if (allocation == null) {
                continue;
            }
            total += safe(allocation.getAmount());
        }
        return total;
    }

    public static double spentInSource(double sourceAmount, List<TransferBucketAllocation> allocations) {
        return sourceAmount - allocatedTotal(allocations);
    }

    public static TransferGroupValidationResult validate(double sourceAmount,
                                                         String sourceEnvelopeName,
                                                         List<TransferBucketAllocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return TransferGroupValidationResult.invalid("Add at least one transfer bucket");
        }

        Set<String> destinations = new HashSet<>();
        double allocated = 0d;
        for (TransferBucketAllocation allocation : allocations) {
            if (allocation == null) {
                return TransferGroupValidationResult.invalid("Transfer bucket is missing");
            }
            String destination = normalize(allocation.getToEnvelope());
            if (destination.isEmpty()) {
                return TransferGroupValidationResult.invalid("Choose a destination for every transfer bucket");
            }
            if (destination.equals(normalize(sourceEnvelopeName))) {
                return TransferGroupValidationResult.invalid("Transfer destination must be a different envelope");
            }
            if (!destinations.add(destination.toLowerCase(Locale.US))) {
                return TransferGroupValidationResult.invalid("Each destination can only appear once per transfer");
            }
            if (allocation.getAmount() <= 0d) {
                return TransferGroupValidationResult.invalid("Every transfer bucket must be greater than $0.00");
            }
            allocated += allocation.getAmount();
        }

        if (allocated - sourceAmount > 0.0001d) {
            return TransferGroupValidationResult.invalid(String.format(Locale.getDefault(),
                    "Transfers exceed the total by $%.2f", allocated - sourceAmount));
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
