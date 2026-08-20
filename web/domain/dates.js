/** Calendar helpers aligned with Java Calendar (local timezone, month add clamps the day). */

const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONTHS_LONG = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'];
const MONTH_PATTERN = /^\d{4}-\d{2}$/;

export function startOfDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function daysInMonth(year, month0) {
  return new Date(year, month0 + 1, 0).getDate();
}

/** Java Calendar.add(MONTH, n): keep day-of-month or clamp to last valid day. */
export function addMonths(date, n) {
  const year = date.getFullYear();
  const month = date.getMonth();
  const day = date.getDate();
  const targetMonthIndex = month + n;
  const target = new Date(year, targetMonthIndex, 1);
  const max = daysInMonth(target.getFullYear(), target.getMonth());
  return new Date(target.getFullYear(), target.getMonth(), Math.min(day, max));
}

export function ymd(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function yearMonth(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}

export function parseIsoDate(value) {
  if (!value) return null;
  const trimmed = String(value).trim();
  const iso = /^(\d{4})-(\d{2})-(\d{2})/.exec(trimmed);
  if (iso) {
    return startOfDay(new Date(Number(iso[1]), Number(iso[2]) - 1, Number(iso[3])));
  }
  return parseDisplayDate(trimmed);
}

export function parseDisplayDate(value) {
  if (!value) return null;
  const trimmed = String(value).trim();
  const m = /^([A-Za-z]{3}) (\d{1,2}), (\d{4})$/.exec(trimmed);
  if (!m) return null;
  const month = MONTHS_SHORT.findIndex((name) => name.toLowerCase() === m[1].toLowerCase());
  if (month < 0) return null;
  return startOfDay(new Date(Number(m[3]), month, Number(m[2])));
}

export function formatDisplayDate(date) {
  return `${MONTHS_SHORT[date.getMonth()]} ${date.getDate()}, ${date.getFullYear()}`;
}

export function formatMonthTitle(yearMonthValue) {
  const parsed = parseYearMonth(yearMonthValue);
  if (!parsed) return yearMonthValue || '';
  return `${MONTHS_LONG[parsed.month0]} ${parsed.year}`;
}

export function parseYearMonth(value) {
  const normalized = normalizeMonth(value);
  if (!normalized) return null;
  const year = Number(normalized.slice(0, 4));
  const month0 = Number(normalized.slice(5, 7)) - 1;
  return { year, month0, date: new Date(year, month0, 1) };
}

export function normalizeMonth(month) {
  if (month == null) return null;
  const trimmed = String(month).trim();
  if (!MONTH_PATTERN.test(trimmed)) return null;
  return trimmed;
}

export function realCurrentMonth(now = new Date()) {
  return yearMonth(now);
}

export function shouldRollover(storedMonth, actualMonth, now = new Date()) {
  let normalizedActual = normalizeMonth(actualMonth);
  if (normalizedActual == null) {
    normalizedActual = realCurrentMonth(now);
  }
  const normalizedStored = normalizeMonth(storedMonth);
  return normalizedStored == null || normalizedStored !== normalizedActual;
}

export function addMonthsYearMonth(yearMonthValue, delta) {
  const parsed = parseYearMonth(yearMonthValue);
  if (!parsed) return yearMonthValue;
  return yearMonth(addMonths(parsed.date, delta));
}

export function firstDayOfMonth(yearMonthValue) {
  const parsed = parseYearMonth(yearMonthValue);
  if (!parsed) return null;
  return parsed.date;
}

export function lastDayOfMonth(yearMonthValue) {
  const parsed = parseYearMonth(yearMonthValue);
  if (!parsed) return null;
  return new Date(parsed.year, parsed.month0, daysInMonth(parsed.year, parsed.month0));
}

export { MONTHS_SHORT, MONTHS_LONG };
