import { parseIsoDate, parseDisplayDate } from './dates.js';

export function isIsoDateOutsideFilterRange(isoDateYyyyMmDd, filterStartDisplay, filterEndDisplay) {
  if (!isoDateYyyyMmDd || !String(isoDateYyyyMmDd).trim()) return false;
  const receiptDate = parseIsoDate(String(isoDateYyyyMmDd).trim());
  if (!receiptDate) return false;
  const start = filterStartDisplay ? parseDisplayDate(String(filterStartDisplay).trim()) : null;
  const end = filterEndDisplay ? parseDisplayDate(String(filterEndDisplay).trim()) : null;
  if (start && receiptDate < start) return true;
  if (end && receiptDate > end) return true;
  return false;
}
