package com.example.envelopemoney;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralizes startup month-state repair and rollover so the activity only adopts a fully valid state.
 * The helper works on a deep copy of the envelope list to avoid half-applied mutations when repair fails.
 */
public final class MonthRolloverHelper {
    private static final Gson GSON = new Gson();

    public static final class Result {
        private final List<Envelope> envelopes;
        private final String activeMonth;
        private final boolean requiresPersistence;
        private final boolean rolledOver;
        private final List<String> warnings;

        Result(List<Envelope> envelopes,
               String activeMonth,
               boolean requiresPersistence,
               boolean rolledOver,
               List<String> warnings) {
            this.envelopes = envelopes;
            this.activeMonth = activeMonth;
            this.requiresPersistence = requiresPersistence;
            this.rolledOver = rolledOver;
            this.warnings = warnings;
        }

        public List<Envelope> getEnvelopes() {
            return envelopes;
        }

        public String getActiveMonth() {
            return activeMonth;
        }

        public boolean requiresPersistence() {
            return requiresPersistence;
        }

        public boolean rolledOver() {
            return rolledOver;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

    private MonthRolloverHelper() {
    }

    public static Result prepareForLaunch(List<Envelope> sourceEnvelopes,
                                          String storedMonth,
                                          String actualMonth,
                                          boolean carryOver) {
        String resolvedActualMonth = MonthTracker.normalizeMonth(actualMonth);
        if (resolvedActualMonth == null) {
            resolvedActualMonth = MonthTracker.getRealCurrentMonth();
        }
        String resolvedStoredMonth = MonthTracker.normalizeMonth(storedMonth);
        String fallbackMonth = resolvedStoredMonth != null ? resolvedStoredMonth : resolvedActualMonth;

        List<String> warnings = new ArrayList<>();
        List<Envelope> workingCopy = deepCopy(sourceEnvelopes);
        if (workingCopy == null) {
            workingCopy = new ArrayList<>();
            warnings.add("Recovered from unreadable envelope state");
        }

        sanitizeEnvelopeList(workingCopy, fallbackMonth, warnings);

        boolean rolloverNeeded = MonthTracker.shouldRollover(resolvedStoredMonth, resolvedActualMonth);
        if (rolloverNeeded) {
            for (Envelope envelope : workingCopy) {
                applyRollover(envelope, fallbackMonth, resolvedActualMonth, carryOver, warnings);
            }
        } else {
            for (Envelope envelope : workingCopy) {
                envelope.sanitizeState(resolvedActualMonth);
                envelope.rebuildMonthData(resolvedActualMonth);
                if (envelope.getManualRemaining() == null) {
                    envelope.setRemaining(envelope.getMonthlyData(resolvedActualMonth).remaining);
                }
            }
        }

        return new Result(workingCopy,
                resolvedActualMonth,
                rolloverNeeded || resolvedStoredMonth == null || !warnings.isEmpty(),
                rolloverNeeded,
                warnings);
    }

    private static void sanitizeEnvelopeList(List<Envelope> envelopes, String fallbackMonth, List<String> warnings) {
        envelopes.removeIf(envelope -> envelope == null);
        for (Envelope envelope : envelopes) {
            try {
                envelope.sanitizeState(fallbackMonth);
            } catch (RuntimeException exception) {
                warnings.add("Repaired envelope state for " + envelope.getName());
                envelope.getTransactions().clear();
                envelope.getTransfers().clear();
                envelope.getMonthlyDataMap().clear();
                envelope.sanitizeState(fallbackMonth);
            }
        }
    }

    /**
     * Applies month transition on one envelope. Invariant: the user-facing monthly budget
     * ({@link Envelope#getLimit()}) stays equal to {@code originalLimit}; carry increases the
     * available pool via {@link Envelope#setRemaining}, {@link Envelope#setManualRemaining},
     * baselines, and {@link Envelope#replaceMonthData} — not by inflating {@code limit}.
     */
    private static void applyRollover(Envelope envelope,
                                      String sourceMonth,
                                      String targetMonth,
                                      boolean carryOver,
                                      List<String> warnings) {
        envelope.sanitizeState(sourceMonth);
        envelope.rebuildMonthData(sourceMonth);

        double originalLimit = safeFinite(envelope.getOriginalLimit(), envelope.getLimit());
        double sourceRemaining = resolveCarryOverRemaining(envelope, originalLimit);
        double targetLimit = carryOver ? originalLimit + sourceRemaining : originalLimit;
        targetLimit = safeFinite(targetLimit, originalLimit);

        envelope.setOriginalLimit(originalLimit);
        // Keep envelope.limit at the base monthly budget; targetLimit is the effective pool for the new month.
        envelope.setLimit(originalLimit);
        envelope.setBaselineLimit(targetLimit);
        envelope.setBaselineRemaining(targetLimit);
        envelope.setManualRemaining(carryOver ? targetLimit : null);
        envelope.setRemaining(targetLimit);
        envelope.replaceMonthData(targetMonth, targetLimit);

        if (!targetMonth.equals(sourceMonth)) {
            envelope.rebuildMonthData(sourceMonth);
        }
        if (carryOver && targetLimit < 0d) {
            warnings.add("Negative carry-over was normalized for " + envelope.getName());
        }
    }

    private static double resolveCarryOverRemaining(Envelope envelope, double fallbackValue) {
        Double manualRemaining = envelope.getManualRemaining();
        if (manualRemaining != null && Double.isFinite(manualRemaining)) {
            return manualRemaining;
        }
        double remaining = envelope.getRemaining();
        if (Double.isFinite(remaining)) {
            return remaining;
        }
        return fallbackValue;
    }

    private static double safeFinite(double primary, double fallback) {
        if (Double.isFinite(primary)) {
            return primary;
        }
        if (Double.isFinite(fallback)) {
            return fallback;
        }
        return 0d;
    }

    private static List<Envelope> deepCopy(List<Envelope> envelopes) {
        Type type = new TypeToken<ArrayList<Envelope>>() { }.getType();
        String json = GSON.toJson(envelopes == null ? new ArrayList<Envelope>() : envelopes, type);
        return GSON.fromJson(json, type);
    }
}
