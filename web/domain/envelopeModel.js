import { parseIsoDate, yearMonth } from './dates.js';

function safe(v) {
  const n = Number(v);
  if (!Number.isFinite(n)) return 0;
  return n;
}

export function createTransaction(envelopeName, amount, date, comment) {
  const tx = {
    envelopeName: envelopeName != null ? envelopeName : 'Uncategorized',
    amount: Number(amount) || 0,
    date: date != null ? String(date) : '',
    comment: comment != null ? String(comment) : '',
    month: null,
    transferId: null,
    transferBucketId: null,
    splitPurchaseGroupId: null,
    splitPurchaseBucketId: null,
    recurring: false,
    recurringFrequency: null,
    recurringDays: [],
    recurringSeriesId: null,
    recurringTemplate: false,
    receiptImageUri: null,
  };
  if (tx.date === '') {
    tx.month = yearMonth(new Date());
  } else {
    tx.month = parseDateToMonth(tx.date);
  }
  return tx;
}

export function setTransactionDate(tx, date) {
  tx.date = date;
  if (date) {
    const parsed = parseDateToMonth(date);
    if (parsed) tx.month = parsed;
  }
}

export function parseDateToMonth(dateStr) {
  if (!dateStr) return null;
  const parsed = parseIsoDate(dateStr);
  if (!parsed) return null;
  return yearMonth(parsed);
}

export function createEnvelope(name, limit) {
  const lim = Number(limit) || 0;
  return {
    name,
    limit: lim,
    originalLimit: lim,
    remaining: lim,
    transactions: [],
    selected: true,
    monthlyData: {},
    transfers: [],
    accountBalance: null,
    manualRemaining: null,
    baselineLimit: 0,
    baselineRemaining: 0,
  };
}

export function getTransactions(envelope) {
  if (!envelope.transactions) envelope.transactions = [];
  return envelope.transactions;
}

export function getTransfers(envelope) {
  if (!envelope.transfers) envelope.transfers = [];
  return envelope.transfers;
}

export function getMonthlyDataMap(envelope) {
  if (!envelope.monthlyData) envelope.monthlyData = {};
  return envelope.monthlyData;
}

export function getMonthlyData(envelope, month) {
  const map = getMonthlyDataMap(envelope);
  if (!map[month]) {
    map[month] = { limit: envelope.limit, remaining: envelope.remaining, transactions: [] };
  }
  if (!map[month].transactions) map[month].transactions = [];
  return map[month];
}

export function addTransfer(envelope, id, bucketId, toEnvelope, amount) {
  getTransfers(envelope).push({
    id,
    bucketId,
    toEnvelope,
    amount,
  });
}

export function calculateRemaining(envelope, currentMonth) {
  if (currentMonth == null) return;
  let spentThisMonth = 0;
  for (const t of getTransactions(envelope)) {
    const tm = t != null ? t.month : null;
    if (tm != null && tm === currentMonth) {
      spentThisMonth += safe(t.amount);
    }
  }
  if (envelope.manualRemaining != null && Number.isFinite(envelope.manualRemaining)) {
    const baseline = Number.isFinite(envelope.baselineRemaining)
      ? envelope.baselineRemaining
      : envelope.manualRemaining;
    envelope.remaining = baseline - spentThisMonth;
  } else {
    envelope.remaining = safe(envelope.limit) - spentThisMonth;
  }
}

export function addTransaction(envelope, t, currentMonth) {
  t.envelopeName = envelope.name;
  getTransactions(envelope).push(t);
  if (t.month === currentMonth) {
    initializeMonth(envelope, currentMonth, false);
    if (envelope.manualRemaining != null && Number.isFinite(envelope.manualRemaining)) {
      envelope.manualRemaining -= t.amount;
      envelope.remaining = envelope.manualRemaining;
    } else {
      calculateRemaining(envelope, currentMonth);
    }
  }
}

export function initializeMonth(envelope, month, carryOver) {
  const monthlyData = getMonthlyDataMap(envelope);
  if (!monthlyData[month]) {
    const previousMonth = previousYearMonth(month);
    const previousData = previousMonth ? monthlyData[previousMonth] : null;
    if (carryOver && previousData) {
      const base = Number.isFinite(envelope.originalLimit) ? envelope.originalLimit : 0;
      const prevLeft = Number.isFinite(previousData.remaining) ? previousData.remaining : 0;
      const effectivePool = base + prevLeft;
      monthlyData[month] = { limit: effectivePool, remaining: effectivePool, transactions: [] };
    } else {
      monthlyData[month] = {
        limit: envelope.originalLimit,
        remaining: envelope.originalLimit,
        transactions: [],
      };
    }
  }
  const currentData = monthlyData[month];
  if (!currentData.transactions) currentData.transactions = [];
  currentData.transactions.length = 0;
  let spent = 0;
  for (const t of getTransactions(envelope)) {
    if (t && t.month === month) {
      spent += t.amount;
      currentData.transactions.push(t);
    }
  }
  currentData.remaining = currentData.limit - spent;
}

export function sanitizeState(envelope, fallbackMonth) {
  getTransactions(envelope);
  getTransfers(envelope);
  getMonthlyDataMap(envelope);
  if (!Number.isFinite(envelope.limit)) envelope.limit = 0;
  if (!Number.isFinite(envelope.originalLimit)) envelope.originalLimit = envelope.limit;
  if (!Number.isFinite(envelope.remaining)) envelope.remaining = envelope.originalLimit;
  if (envelope.manualRemaining != null && !Number.isFinite(envelope.manualRemaining)) {
    envelope.manualRemaining = null;
  }
  if (!Number.isFinite(envelope.baselineLimit)) envelope.baselineLimit = envelope.originalLimit;
  if (!Number.isFinite(envelope.baselineRemaining)) {
    envelope.baselineRemaining = envelope.manualRemaining != null
      ? envelope.manualRemaining
      : envelope.remaining;
  }
  migrateLegacyTransactions(envelope, fallbackMonth);
}

export function replaceMonthData(envelope, month, monthLimit) {
  getMonthlyDataMap(envelope)[month] = {
    limit: safe(monthLimit),
    remaining: safe(monthLimit),
    transactions: [],
  };
  rebuildMonthData(envelope, month);
}

export function rebuildMonthData(envelope, month) {
  const monthData = getMonthlyData(envelope, month);
  monthData.transactions = [];
  if (!Number.isFinite(monthData.limit)) {
    monthData.limit = safe(envelope.originalLimit);
  }
  let spent = 0;
  for (const transaction of getTransactions(envelope)) {
    if (transaction && transaction.month === month) {
      monthData.transactions.push(transaction);
      spent += safe(transaction.amount);
    }
  }
  monthData.remaining = monthData.limit - spent;
}

function previousYearMonth(currentMonth) {
  const parsed = parseIsoDate(`${currentMonth}-01`);
  if (!parsed) return null;
  const prev = new Date(parsed.getFullYear(), parsed.getMonth() - 1, 1);
  return yearMonth(prev);
}

export function migrateLegacyTransactions(envelope, defaultMonthForNull) {
  const monthlyData = getMonthlyDataMap(envelope);
  let data = monthlyData[defaultMonthForNull];
  if (!data) {
    data = { limit: envelope.originalLimit, remaining: envelope.originalLimit, transactions: [] };
    monthlyData[defaultMonthForNull] = data;
  }
  if (!data.transactions) data.transactions = [];
  for (const t of getTransactions(envelope)) {
    if (t.month == null) {
      let derivedMonth = parseDateToMonth(t.date);
      if (derivedMonth == null) derivedMonth = defaultMonthForNull;
      t.month = derivedMonth;
      let correctData = monthlyData[derivedMonth];
      if (!correctData) {
        correctData = { limit: envelope.originalLimit, remaining: envelope.originalLimit, transactions: [] };
        monthlyData[derivedMonth] = correctData;
      }
      if (!correctData.transactions) correctData.transactions = [];
      correctData.transactions.push(t);
    }
  }
  let total = 0;
  for (const t of data.transactions) total += t.amount;
  data.remaining = data.limit - total;
}

export function monthSpendForEnvelope(envelope, currentMonth) {
  if (!envelope || currentMonth == null) return 0;
  let spent = 0;
  for (const transaction of getTransactions(envelope)) {
    if (!transaction) continue;
    if (transaction.month != null && transaction.month === currentMonth) {
      spent += safe(transaction.amount);
    }
  }
  return spent;
}

export function deepCopyEnvelopes(envelopes) {
  return JSON.parse(JSON.stringify(envelopes == null ? [] : envelopes));
}
