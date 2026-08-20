import { roundToCents } from './moneyMath.js';
import { addMonthsYearMonth, MONTHS_LONG, MONTHS_SHORT, parseYearMonth } from './dates.js';

const DEFAULT_LAST_N = 3;
const MIN_BAR_SCALE = 0.01;
const MONTH_PATTERN = /^\d{4}-\d{2}$/;

function safe(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return 0;
  return n;
}

export function normalizeLastN(lastNMonths) {
  if (lastNMonths === 6 || lastNMonths === 12) return lastNMonths;
  return DEFAULT_LAST_N;
}

export function normalizeMonth(month) {
  if (month == null) return null;
  const trimmed = String(month).trim();
  if (!MONTH_PATTERN.test(trimmed)) return null;
  return trimmed;
}

export function countsAsSpend(tx, includeTransfers) {
  if (!tx) return false;
  if (includeTransfers) return true;
  const transferId = tx.transferId;
  return transferId == null || String(transferId).trim() === '';
}

export function monthKeys(endMonth, lastNMonths) {
  const end = normalizeMonth(endMonth);
  const count = normalizeLastN(lastNMonths);
  const keys = [];
  if (!end) return keys;
  for (let i = count - 1; i >= 0; i--) {
    keys.push(addMonthsYearMonth(end, -i));
  }
  return keys;
}

function selectedPonds(envelopes, pondNames) {
  const list = envelopes == null ? [] : envelopes;
  const filter = new Set();
  if (pondNames) {
    for (const name of pondNames) {
      if (name == null) continue;
      const trimmed = String(name).trim();
      if (trimmed) filter.add(trimmed);
    }
  }
  const allPonds = filter.size === 0;
  const selected = [];
  for (const envelope of list) {
    if (!envelope || envelope.name == null) continue;
    if (allPonds || filter.has(String(envelope.name).trim())) {
      selected.push(envelope);
    }
  }
  return selected;
}

export function monthSpend(envelope, month, includeTransfers) {
  if (!envelope || month == null) return 0;
  const transactions = envelope.transactions || [];
  let spent = 0;
  for (const transaction of transactions) {
    if (!transaction) continue;
    if (transaction.month !== month) continue;
    if (!countsAsSpend(transaction, includeTransfers)) continue;
    spent += safe(transaction.amount);
  }
  return roundToCents(spent);
}

export function analyze(envelopes, query = {}) {
  const includeTransfers = !!query.includeTransfers;
  const months = monthKeys(query.endMonth, query.lastNMonths);
  const selected = selectedPonds(envelopes, query.pondNames);
  const monthRows = [];
  const overBudget = [];
  const byPond = [];
  const thisMonth = [];
  let hasSpendInRange = false;

  for (const month of months) {
    let totalSpend = 0;
    let totalLimit = 0;
    for (const envelope of selected) {
      const spend = monthSpend(envelope, month, includeTransfers);
      const limit = roundToCents(safe(envelope.limit));
      totalSpend = roundToCents(totalSpend + spend);
      totalLimit = roundToCents(totalLimit + limit);
      if (spend > limit) {
        overBudget.push({
          month,
          pondName: envelope.name,
          spend,
          limit,
          overBy: roundToCents(spend - limit),
        });
      }
    }
    if (totalSpend !== 0) hasSpendInRange = true;
    monthRows.push({ month, totalSpend, totalLimit });
  }

  const endMonth = months.length ? months[months.length - 1] : null;
  for (const envelope of selected) {
    let rangeSpend = 0;
    for (const month of months) {
      rangeSpend = roundToCents(rangeSpend + monthSpend(envelope, month, includeTransfers));
    }
    byPond.push({ pondName: envelope.name, spend: rangeSpend });
    if (endMonth) {
      const spend = monthSpend(envelope, endMonth, includeTransfers);
      const limit = roundToCents(safe(envelope.limit));
      thisMonth.push({
        pondName: envelope.name,
        spend,
        limit,
        remaining: roundToCents(safe(envelope.remaining)),
        overBudget: spend > limit,
      });
    }
  }

  overBudget.sort((left, right) => {
    const byOver = right.overBy - left.overBy;
    if (byOver !== 0) return byOver;
    if (right.month !== left.month) return right.month < left.month ? -1 : 1;
    return String(left.pondName).localeCompare(String(right.pondName), undefined, { sensitivity: 'base' });
  });
  byPond.sort((left, right) => {
    const bySpend = right.spend - left.spend;
    if (bySpend !== 0) return bySpend;
    return String(left.pondName).localeCompare(String(right.pondName), undefined, { sensitivity: 'base' });
  });

  return { months: monthRows, overBudget, byPond, thisMonth, hasSpendInRange };
}

export function barScaleMax(amounts) {
  let max = 0;
  if (amounts) {
    for (const amount of amounts) {
      const n = Number(amount);
      if (Number.isFinite(n) && n > max) max = n;
    }
  }
  return Math.max(roundToCents(max), MIN_BAR_SCALE);
}

export function shortMonthLabel(yyyyMm) {
  const parsed = parseYearMonth(yyyyMm);
  if (!parsed) return yyyyMm || '';
  return MONTHS_SHORT[parsed.month0];
}

export function fullMonthLabel(yyyyMm) {
  const parsed = parseYearMonth(yyyyMm);
  if (!parsed) return yyyyMm || '';
  return `${MONTHS_LONG[parsed.month0]} ${parsed.year}`;
}
