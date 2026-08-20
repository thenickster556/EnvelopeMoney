import { addMonths, daysInMonth, startOfDay } from './dates.js';

/**
 * Bills-period filter start date (end date is today). Matches Android BillsDayAnchor.
 */
export function computeAnchorDate(today, billsDaysOfMonth) {
  if (!billsDaysOfMonth || billsDaysOfMonth.length === 0) return null;
  const set = [];
  for (const d of billsDaysOfMonth) {
    if (d != null && d >= 1 && d <= 31 && !set.includes(d)) {
      set.push(d);
    }
  }
  set.sort((a, b) => a - b);
  if (set.length === 0) return null;

  const probe = startOfDay(today);
  const todayDom = probe.getDate();

  for (let monthsBack = 0; monthsBack <= 12; monthsBack++) {
    const monthCal = addMonths(probe, -monthsBack);
    const maxInMonth = daysInMonth(monthCal.getFullYear(), monthCal.getMonth());
    const upperBound = monthsBack === 0 ? Math.min(todayDom, maxInMonth) : maxInMonth;

    let best = -1;
    for (const d of set) {
      if (d <= upperBound && d <= maxInMonth) {
        best = Math.max(best, d);
      }
    }
    if (best > 0) {
      if (monthsBack === 0) {
        if (set.length > 1 && todayDom === best) {
          const prior = lower(set, best);
          if (prior != null) {
            return new Date(monthCal.getFullYear(), monthCal.getMonth(), Math.min(prior, maxInMonth));
          }
        } else if (set.length === 1 && todayDom === best) {
          return anchorOnDayInPreviousMonth(probe, best);
        }
      }
      return new Date(monthCal.getFullYear(), monthCal.getMonth(), Math.min(best, maxInMonth));
    }
  }
  return null;
}

function lower(sortedUnique, value) {
  let found = null;
  for (const d of sortedUnique) {
    if (d < value) found = d;
  }
  return found;
}

function anchorOnDayInPreviousMonth(todayAtMidnight, dayOfMonth) {
  const anchor = addMonths(todayAtMidnight, -1);
  const maxInPrev = daysInMonth(anchor.getFullYear(), anchor.getMonth());
  return new Date(anchor.getFullYear(), anchor.getMonth(), Math.min(dayOfMonth, maxInPrev));
}
