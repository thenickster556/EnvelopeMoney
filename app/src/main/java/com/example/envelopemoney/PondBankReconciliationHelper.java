package com.example.envelopemoney;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TreeSet;

/**
 * Bank-vs-budget reconciliation for ponds with an entered account balance and configured paydays.
 * {@link #getStillToDepositForMonth()} is the schedule gap as of today (expected in bank minus actual).
 */
public final class PondBankReconciliationHelper {

    private PondBankReconciliationHelper() {
    }

    public static boolean isReconciliationModeActive(Double accountBalance, List<Integer> paydays) {
        return accountBalance != null
                && paydays != null
                && !paydays.isEmpty();
    }

    public static Result compute(double monthTargetRaw,
                                 Double accountBalanceRaw,
                                 List<Integer> paydayDaysOfMonth,
                                 Calendar visibleMonth,
                                 Calendar today) {
        double monthTargetRounded = MoneyMath.roundToCents(monthTargetRaw);
        if (accountBalanceRaw == null) {
            return Result.inactive(monthTargetRounded);
        }

        double monthTarget = MoneyMath.roundToCents(monthTargetRaw);
        double inBank = MoneyMath.roundToCents(accountBalanceRaw);

        Calendar month = (Calendar) visibleMonth.clone();
        month.set(Calendar.HOUR_OF_DAY, 0);
        month.set(Calendar.MINUTE, 0);
        month.set(Calendar.SECOND, 0);
        month.set(Calendar.MILLISECOND, 0);

        Calendar todayMidnight = (Calendar) today.clone();
        todayMidnight.set(Calendar.HOUR_OF_DAY, 0);
        todayMidnight.set(Calendar.MINUTE, 0);
        todayMidnight.set(Calendar.SECOND, 0);
        todayMidnight.set(Calendar.MILLISECOND, 0);

        List<Integer> paydaysInMonth = resolvePaydayDaysInMonth(paydayDaysOfMonth, month);
        int paydaysPassed = countPaydaysOnOrBefore(paydaysInMonth, todayMidnight, month);

        int n = paydaysInMonth.size();
        double perPayday = n == 0 ? 0d : MoneyMath.roundToCents(monthTarget / n);
        double expectedByToday = MoneyMath.roundToCents(perPayday * paydaysPassed);
        double stillToDepositForMonth =
                MoneyMath.roundToCents(Math.max(0d, expectedByToday - inBank));
        double fullMonthStillToDeposit =
                MoneyMath.roundToCents(Math.max(0d, monthTarget - inBank));
        double behindSchedule = stillToDepositForMonth;
        double aheadOfTarget = MoneyMath.roundToCents(Math.max(0d, inBank - monthTarget));

        return new Result(true, monthTarget, inBank, stillToDepositForMonth,
                fullMonthStillToDeposit, perPayday, n, paydaysPassed, expectedByToday, behindSchedule, aheadOfTarget);
    }

    static List<Integer> resolvePaydayDaysInMonth(List<Integer> paydayDaysOfMonth, Calendar month) {
        if (paydayDaysOfMonth == null || paydayDaysOfMonth.isEmpty()) {
            return new ArrayList<>();
        }
        int maxInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        TreeSet<Integer> unique = new TreeSet<>();
        for (Integer d : paydayDaysOfMonth) {
            if (d != null && d >= 1 && d <= 31) {
                unique.add(Math.min(d, maxInMonth));
            }
        }
        return new ArrayList<>(unique);
    }

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
        int todayDom = today.get(Calendar.DAY_OF_MONTH);
        int count = 0;
        for (int d : paydaysInMonth) {
            if (d <= todayDom) {
                count++;
            }
        }
        return count;
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
                       double aheadOfTarget) {
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
        }

        static Result inactive(double monthTarget) {
            return new Result(false, MoneyMath.roundToCents(monthTarget), 0d, 0d, 0d,
                    0d, 0, 0, 0d, 0d, 0d);
        }

        public boolean isActive() {
            return active;
        }

        public double getMonthTarget() {
            return monthTarget;
        }

        public double getInBank() {
            return inBank;
        }

        public double getStillToDepositForMonth() {
            return stillToDepositForMonth;
        }

        public double getFullMonthStillToDeposit() {
            return fullMonthStillToDeposit;
        }

        public double getPerPayday() {
            return perPayday;
        }

        public int getPaydaysInMonth() {
            return paydaysInMonth;
        }

        public int getPaydaysPassed() {
            return paydaysPassed;
        }

        public double getExpectedInBankByToday() {
            return expectedInBankByToday;
        }

        public double getBehindSchedule() {
            return behindSchedule;
        }

        public double getAheadOfTarget() {
            return aheadOfTarget;
        }
    }
}
