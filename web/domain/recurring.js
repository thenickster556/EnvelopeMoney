import { parseIsoDate, ymd, daysInMonth } from './dates.js';
import { createTransaction, addTransaction, getTransactions } from './envelopeModel.js';
import { randomUUID } from './id.js';

/** Java Calendar.DAY_OF_WEEK: Sunday=1 … Saturday=7. JS Date.getDay(): Sunday=0. */
export function javaDayOfWeek(date) {
  return date.getDay() + 1;
}

export function ensureRecurringTransactions(envelopes, activeMonth) {
  let changed = false;
  for (const envelope of envelopes) {
    const snapshot = [...getTransactions(envelope)];
    for (const template of snapshot) {
      if (!template.recurring || !template.recurringTemplate) continue;
      if (template.transferId) continue;
      if (!template.recurringFrequency || !template.recurringDays || template.recurringDays.length === 0) {
        continue;
      }
      if (!template.recurringSeriesId) {
        template.recurringSeriesId = randomUUID();
        changed = true;
      }
      const dates = getRecurringDatesForMonth(template, activeMonth);
      for (const date of dates) {
        if (hasRecurringOccurrence(envelope, template.recurringSeriesId, date)) continue;
        const generated = createTransaction(template.envelopeName, template.amount, date, template.comment);
        generated.recurring = true;
        generated.recurringFrequency = template.recurringFrequency;
        generated.recurringDays = [...template.recurringDays];
        generated.recurringSeriesId = template.recurringSeriesId;
        generated.recurringTemplate = false;
        addTransaction(envelope, generated, activeMonth);
        changed = true;
      }
    }
  }
  return changed;
}

export function getRecurringDatesForMonth(template, month) {
  const dates = [];
  const anchorDate = parseIsoDate(template.date);
  const monthStart = parseIsoDate(`${month}-01`);
  if (!monthStart) return dates;
  const maxDay = daysInMonth(monthStart.getFullYear(), monthStart.getMonth());
  const monthEnd = new Date(monthStart.getFullYear(), monthStart.getMonth(), maxDay);
  const frequency = template.recurringFrequency;
  const selectedDays = new Set(template.recurringDays || []);
  let anchorWeekStart = null;
  if (frequency === 'bi-weekly' && anchorDate) {
    anchorWeekStart = startOfWeekSunday(anchorDate);
  }
  const cursor = new Date(monthStart);
  while (cursor <= monthEnd) {
    if (anchorDate && cursor < anchorDate) {
      cursor.setDate(cursor.getDate() + 1);
      continue;
    }
    let include = false;
    if (frequency === 'monthly') {
      include = selectedDays.has(cursor.getDate());
    } else if (frequency === 'weekly') {
      include = selectedDays.has(javaDayOfWeek(cursor));
    } else if (frequency === 'bi-weekly' && anchorWeekStart) {
      if (selectedDays.has(javaDayOfWeek(cursor))) {
        const candidateWeekStart = startOfWeekSunday(cursor);
        const diffMs = candidateWeekStart.getTime() - anchorWeekStart.getTime();
        const weeks = Math.abs(Math.trunc(diffMs / (7 * 24 * 60 * 60 * 1000)));
        include = weeks % 2 === 0;
      }
    }
    if (include) dates.push(ymd(cursor));
    cursor.setDate(cursor.getDate() + 1);
  }
  return dates;
}

function startOfWeekSunday(date) {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  d.setDate(d.getDate() - d.getDay());
  return d;
}

function hasRecurringOccurrence(envelope, seriesId, date) {
  for (const transaction of getTransactions(envelope)) {
    if (transaction.recurringSeriesId === seriesId && transaction.date === date) return true;
  }
  return false;
}
