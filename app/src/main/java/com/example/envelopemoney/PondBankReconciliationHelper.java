package com.example.envelopemoney;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TreeSet;

/**
 * Bank-vs-budget reconciliation for ponds with an entered account balance and configured paydays.
 *
 * <p><b>Purpose:</b> Estimate spendable Remaining from the user's In-bank checkpoint plus payday
 * slices of the monthly Limit that have already arrived, minus this month's spending. Still to
 * deposit is only the Limit money tied to paydays that have not yet arrived.
 *
 * <p><b>Assumptions:</b> Account is a manual checkpoint (not auto-updated on payday). Limit is the
 * monthly budget and is never inflated by unlocks. A payday counts as passed on its calendar day
 * ({@code dayOfMonth <= today}). Progress resets automatically each calendar month.
 *
 * <p><b>Edge cases:</b> Null Account → inactive. Day 31 clamps to month length and duplicates
 * collapse. Past months treat all paydays as passed; future months treat none as passed.
 * Remaining may be negative when spend exceeds Account + unlocked.
 */
public final class PondBankReconciliationHelper {

    private PondBankReconciliationHelper() {
    }

    public static boolean isReconciliationModeActive(Double accountBalance, List<Integer> paydays) {
        return accountBalance != null
                && paydays != null
                && !paydays.isEmpty();
    }

    /**
     * Computes payday unlock / still-to-deposit / estimated remaining for one pond.
     *
     * @param monthTargetRaw      monthly Limit (budget); not modified by unlocks
     * @param accountBalanceRaw   In-bank checkpoint, or null to return inactive
     * @param paydayDaysOfMonth   configured days of month (1–31)
     * @param visibleMonth        calendar month being displayed
     * @param today               reference "now" (usually device today)
     * @param monthSpendRaw       sum of this pond's transactions for the active month
     * @return Result with In bank, Still to deposit (unpassed slices), estimated Remaining, progress
     */
    public static Result compute(double monthTargetRaw,
                                 Double accountBalanceRaw,
                                 List<Integer> paydayDaysOfMonth,
                                 Calendar visibleMonth,
                                 Calendar today,
                                 double monthSpendRaw) {
        double monthTarget = MoneyMath.roundToCents(monthTargetRaw);
        if (accountBalanceRaw == null) {
            return Result.inactive(monthTarget);
        }

        double inBank = MoneyMath.roundToCents(accountBalanceRaw);
        double monthSpend = MoneyMath.roundToCents(monthSpendRaw);

        Calendar month = startOfDay(visibleMonth);
        Calendar todayMidnight = startOfDay(today);

        List<Integer> paydaysInMonth = resolvePaydayDaysInMonth(paydayDaysOfMonth, month);
        int paydaysPassed = countPaydaysOnOrBefore(paydaysInMonth, todayMidnight, month);
        int paydayCount = paydaysInMonth.size();

        double unlockedFromPaydays = unlockedAmount(monthTarget, paydayCount, paydaysPassed);
        double stillToDepositForMonth = stillToDepositAmount(monthTarget, paydayCount, paydaysPassed);
        double perPayday = paydayCount == 0
                ? 0d
                : MoneyMath.roundToCents(monthTarget / paydayCount);
        double expectedByToday = unlockedFromPaydays;
        double fullMonthStillToDeposit = monthTarget;
        double aheadOfTarget = MoneyMath.roundToCents(Math.max(0d, inBank - monthTarget));
        double estimatedRemaining = MoneyMath.roundToCents(inBank + unlockedFromPaydays - monthSpend);

        return new Result(
                true,
                monthTarget,
                inBank,
                stillToDepositForMonth,
                fullMonthStillToDeposit,
                perPayday,
                paydayCount,
                paydaysPassed,
                expectedByToday,
                stillToDepositForMonth,
                aheadOfTarget,
                unlockedFromPaydays,
                estimatedRemaining);
    }

    /** Backward-compatible overload treating month spend as zero (preview without transactions). */
    public static Result compute(double monthTargetRaw,
                                 Double accountBalanceRaw,
                                 List<Integer> paydayDaysOfMonth,
                                 Calendar visibleMonth,
                                 Calendar today) {
        return compute(monthTargetRaw, accountBalanceRaw, paydayDaysOfMonth, visibleMonth, today, 0d);
    }

    /**
     * Sum of fair Limit shares for the first {@code paydaysPassed} paydays.
     * Shares always sum to Limit across all N paydays (last share absorbs remainder cents).
     */
    static double unlockedAmount(double monthTarget, int paydayCount, int paydaysPassed) {
        if (paydayCount <= 0 || paydaysPassed <= 0) {
            return 0d;
        }
        int passed = Math.min(paydaysPassed, paydayCount);
        double[] shares = fairPaydayShares(monthTarget, paydayCount);
        double unlocked = 0d;
        for (int i = 0; i < passed; i++) {
            unlocked += shares[i];
        }
        return MoneyMath.roundToCents(unlocked);
    }

    static double stillToDepositAmount(double monthTarget, int paydayCount, int paydaysPassed) {
        if (paydayCount <= 0) {
            return 0d;
        }
        int passed = Math.min(Math.max(paydaysPassed, 0), paydayCount);
        double[] shares = fairPaydayShares(monthTarget, paydayCount);
        double still = 0d;
        for (int i = passed; i < paydayCount; i++) {
            still += shares[i];
        }
        return MoneyMath.roundToCents(still);
    }

    /** Equal Limit shares whose cents sum exactly to {@code monthTarget}. */
    static double[] fairPaydayShares(double monthTarget, int paydayCount) {
        if (paydayCount <= 0) {
            return new double[0];
        }
        int[] equalPercents = new int[paydayCount];
        int base = 100 / paydayCount;
        int remainder = 100 - (base * paydayCount);
        for (int i = 0; i < paydayCount; i++) {
            equalPercents[i] = base;
        }
        // Put leftover percent points on the first shares so they remain near-equal.
        for (int i = 0; i < remainder; i++) {
            equalPercents[i] = equalPercents[i] + 1;
        }
        return MoneyMath.splitTotalByPercents(monthTarget, equalPercents);
    }

    static List<Integer> resolvePaydayDaysInMonth(List<Integer> paydayDaysOfMonth, Calendar month) {
        if (paydayDaysOfMonth == null || paydayDaysOfMonth.isEmpty()) {
            return new ArrayList<>();
        }
        int maxInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        TreeSet<Integer> unique = new TreeSet<>();
        for (Integer day : paydayDaysOfMonth) {
            if (day != null && day >= 1 && day <= 31) {
                unique.add(Math.min(day, maxInMonth));
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * Counts paydays on or before today within {@code month}.
     * Past months → all; future months → none (monthly reset).
     */
    static int countPaydaysOnOrBefore(List<Integer> paydaysInMonth,
                                      Calendar today,
                                      Calendar month) {
        if (paydaysInMonth.isEmpty()) {
            return 0;
        }
        int monthYear = month.get(Calendar.YEAR);
        int monthMonth = month.get(Calendar.MONTH);
        int todayYear = today.get(Calendar.YEAR);
        int todayMonth = today.get(Calendar.MONTH);
        if (monthYear < todayYear || (monthYear == todayYear && monthMonth < todayMonth)) {
            return paydaysInMonth.size();
        }
        if (monthYear > todayYear || (monthYear == todayYear && monthMonth > todayMonth)) {
            return 0;
        }
        int todayDayOfMonth = today.get(Calendar.DAY_OF_MONTH);
        int count = 0;
        for (int day : paydaysInMonth) {
            if (day <= todayDayOfMonth) {
                count++;
            }
        }
        return count;
    }

    private static Calendar startOfDay(Calendar source) {
        Calendar day = (Calendar) source.clone();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day;
    }

    public static final class Result {
        private final boolean active;
        private final double monthTarget;
        private final double inBank;
        private final double stillToDepositForMonth;
        private final double fullMonthStillToDeposit;
        private final double perPayday;
        private final int paydaysInMonth;
        private final int paydaysPassed;
        private final double expectedInBankByToday;
        private final double behindSchedule;
        private final double aheadOfTarget;
        private final double unlockedFromPaydays;
        private final double estimatedRemaining;

        private Result(boolean active,
                       double monthTarget,
                       double inBank,
                       double stillToDepositForMonth,
                       double fullMonthStillToDeposit,
                       double perPayday,
                       int paydaysInMonth,
                       int paydaysPassed,
                       double expectedInBankByToday,
                       double behindSchedule,
                       double aheadOfTarget,
                       double unlockedFromPaydays,
                       double estimatedRemaining) {
            this.active = active;
            this.monthTarget = monthTarget;
            this.inBank = inBank;
            this.stillToDepositForMonth = stillToDepositForMonth;
            this.fullMonthStillToDeposit = fullMonthStillToDeposit;
            this.perPayday = perPayday;
            this.paydaysInMonth = paydaysInMonth;
            this.paydaysPassed = paydaysPassed;
            this.expectedInBankByToday = expectedInBankByToday;
            this.behindSchedule = behindSchedule;
            this.aheadOfTarget = aheadOfTarget;
            this.unlockedFromPaydays = unlockedFromPaydays;
            this.estimatedRemaining = estimatedRemaining;
        }

        static Result inactive(double monthTarget) {
            return new Result(false, MoneyMath.roundToCents(monthTarget), 0d, 0d, 0d,
                    0d, 0, 0, 0d, 0d, 0d, 0d, 0d);
        }

        public boolean isActive() {
            return active;
        }

        /** Monthly Limit used as the payday divider target. */
        public double getMonthTarget() {
            return monthTarget;
        }

        /** User Account checkpoint (In bank). */
        public double getInBank() {
            return inBank;
        }

        /**
         * Money from paydays that have not yet arrived this month
         * ({@code Limit} slices for unpassed days).
         */
        public double getStillToDepositForMonth() {
            return stillToDepositForMonth;
        }

        /** Full-month Limit (all payday slices); kept for callers that show full-month context. */
        public double getFullMonthStillToDeposit() {
            return fullMonthStillToDeposit;
        }

        /** Approximate per-payday share ({@code Limit / N}); display convenience. */
        public double getPerPayday() {
            return perPayday;
        }

        public int getPaydaysInMonth() {
            return paydaysInMonth;
        }

        public int getPaydaysPassed() {
            return paydaysPassed;
        }

        /** Unlocked Limit money from paydays that have arrived (same as {@link #getUnlockedFromPaydays()}). */
        public double getExpectedInBankByToday() {
            return expectedInBankByToday;
        }

        /** Same as {@link #getStillToDepositForMonth()} under the unpassed-slice model. */
        public double getBehindSchedule() {
            return behindSchedule;
        }

        public double getAheadOfTarget() {
            return aheadOfTarget;
        }

        /** Sum of fair Limit shares for paydays already passed. */
        public double getUnlockedFromPaydays() {
            return unlockedFromPaydays;
        }

        /**
         * Estimated spendable Remaining:
         * {@code Account + unlockedFromPaydays − monthSpend}.
         */
        public double getEstimatedRemaining() {
            return estimatedRemaining;
        }
    }
}
