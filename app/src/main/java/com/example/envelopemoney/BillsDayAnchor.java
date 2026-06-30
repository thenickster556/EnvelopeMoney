package com.example.envelopemoney;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

/**
 * Resolves the transaction-filter start date for the bills-period filter (end date is today).
 * <p>
 * Unified rules for any number of configured bills days:
 * <ol>
 *   <li>Find the latest bills day on or before today in the current month; if none, walk backward
 *       month-by-month (e.g. bills 10 and 25, today Apr 5 → Mar 25).</li>
 *   <li>If that anchor is in the current month and today equals that bills day: with multiple bills
 *       days, start at the previous bills day in the configured set (Apr 15 with [1,15] → Apr 1);
 *       with a single bills day, start at the same day-of-month in the previous month.</li>
 *   <li>Otherwise use the walked anchor as-is (May 15 with bills on the 10th → May 10).</li>
 * </ol>
 */
public final class BillsDayAnchor {

    private BillsDayAnchor() {
    }

    /**
     * @param today    calendar instant (time fields may be non-zero; they are cleared)
     * @param billsDaysOfMonth distinct day-of-month values in [1, 31]
     * @return anchor date at start of day, or null if no anchor within 13 months or empty input
     */
    public static Date computeAnchorDate(Calendar today, List<Integer> billsDaysOfMonth) {
        if (billsDaysOfMonth == null || billsDaysOfMonth.isEmpty()) {
            return null;
        }
        TreeSet<Integer> set = new TreeSet<>();
        for (Integer d : billsDaysOfMonth) {
            if (d != null && d >= 1 && d <= 31) {
                set.add(d);
            }
        }
        if (set.isEmpty()) {
            return null;
        }

        Calendar probe = (Calendar) today.clone();
        probe.set(Calendar.HOUR_OF_DAY, 0);
        probe.set(Calendar.MINUTE, 0);
        probe.set(Calendar.SECOND, 0);
        probe.set(Calendar.MILLISECOND, 0);
        int todayDom = probe.get(Calendar.DAY_OF_MONTH);

        for (int monthsBack = 0; monthsBack <= 12; monthsBack++) {
            Calendar monthCal = (Calendar) probe.clone();
            monthCal.add(Calendar.MONTH, -monthsBack);
            int maxInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int upperBound = (monthsBack == 0)
                    ? Math.min(todayDom, maxInMonth)
                    : maxInMonth;

            int best = -1;
            for (int d : set) {
                if (d <= upperBound && d <= maxInMonth) {
                    best = Math.max(best, d);
                }
            }
            if (best > 0) {
                if (monthsBack == 0) {
                    if (set.size() > 1 && todayDom == best) {
                        Integer prior = set.lower(best);
                        if (prior != null) {
                            monthCal.set(Calendar.DAY_OF_MONTH, Math.min(prior, maxInMonth));
                            return monthCal.getTime();
                        }
                    } else if (set.size() == 1 && todayDom == best) {
                        return anchorOnDayInPreviousMonth(probe, best);
                    }
                }
                monthCal.set(Calendar.DAY_OF_MONTH, Math.min(best, maxInMonth));
                return monthCal.getTime();
            }
        }
        return null;
    }

    private static Date anchorOnDayInPreviousMonth(Calendar todayAtMidnight, int dayOfMonth) {
        Calendar anchor = (Calendar) todayAtMidnight.clone();
        anchor.add(Calendar.MONTH, -1);
        int maxInPrev = anchor.getActualMaximum(Calendar.DAY_OF_MONTH);
        anchor.set(Calendar.DAY_OF_MONTH, Math.min(dayOfMonth, maxInPrev));
        return anchor.getTime();
    }
}
