package com.example.envelopemoney;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

/**
 * Resolves the transaction-filter start date for the bills-period filter (end date is today).
 * <p>
 * If exactly one bills day is configured, the anchor is always that day-of-month in the
 * <strong>previous</strong> calendar month (clamped to month length), so the range covers the
 * open billing period through today — e.g. bills on the 12th and today May 13 → April 12, not May 12.
 * <p>
 * If multiple bills days are configured, the anchor is the latest bills day on or before today,
 * walking backward month-by-month when needed (e.g. bills on 10 and 25, today Apr 5 → Mar 25).
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

        if (set.size() == 1) {
            return anchorOnDayInPreviousMonth(probe, set.first());
        }

        for (int monthsBack = 0; monthsBack <= 12; monthsBack++) {
            Calendar monthCal = (Calendar) probe.clone();
            monthCal.add(Calendar.MONTH, -monthsBack);
            int maxInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int todayDom = probe.get(Calendar.DAY_OF_MONTH);
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
                monthCal.set(Calendar.DAY_OF_MONTH, best);
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
