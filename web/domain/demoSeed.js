import { emptyProfile, refreshBalances } from './profileEngine.js';
import {
  addTransaction,
  createEnvelope,
  createTransaction,
  getTransactions,
  rebuildMonthData,
} from './envelopeModel.js';
import { applyTransferGroup } from './transferSync.js';
import { applyGroup } from './splitSync.js';
import { addMonthsYearMonth, daysInMonth, parseYearMonth, realCurrentMonth, ymd } from './dates.js';
import { randomUUID } from './id.js';

export const DEMO_POND_SPECS = [
  { name: 'Groceries', limit: 500, accountBalance: 220 },
  { name: 'Gas', limit: 200, accountBalance: 80 },
  { name: 'Fun', limit: 250, accountBalance: 90 },
  { name: 'Bills', limit: 400, accountBalance: 200 },
  { name: 'Savings', limit: 300, accountBalance: null },
];

export function shouldSeedDemoProfile(profile) {
  return !profile || !Array.isArray(profile.envelopes) || profile.envelopes.length === 0;
}

export function demoMonths(now = new Date()) {
  const current = realCurrentMonth(now);
  const months = [];
  for (let back = 12; back >= 0; back--) {
    months.push(addMonthsYearMonth(current, -back));
  }
  return months;
}

function dateInMonth(month, day) {
  const parsed = parseYearMonth(month);
  const max = daysInMonth(parsed.year, parsed.month0);
  return ymd(new Date(parsed.year, parsed.month0, Math.min(day, max)));
}

function findPond(envelopes, name) {
  return envelopes.find((e) => e.name === name);
}

/**
 * Builds a full demo profile: 5 ponds, 12 prior months + current, mixed spending,
 * 2-bucket transfers, split purchases, and a monthly Bills recurring series.
 * currentMonth stays today so launch rollover does not replay carry.
 */
export function buildDemoProfile(now = new Date()) {
  const profile = emptyProfile(now);
  profile.billsDays = [1, 15];
  profile.paydays = [1, 15];
  profile.transfersVisible = true;
  profile.lastAddTransactionEnvelope = 'Groceries';

  const envelopes = DEMO_POND_SPECS.map((spec) => {
    const pond = createEnvelope(spec.name, spec.limit);
    pond.accountBalance = spec.accountBalance;
    pond.selected = true;
    return pond;
  });
  profile.envelopes = envelopes;

  const months = demoMonths(now);
  const rentSeriesId = 'demo-rent-series';

  months.forEach((month, index) => {
    const groc = 42.15 + (index % 6) * 4.5;
    const gas = 38.2 + (index % 4) * 3.1;
    const fun = 18.75 + (index % 5) * 2.25;
    addSpend('Groceries', groc, dateInMonth(month, 4), 'Market run', month);
    addSpend('Groceries', groc + 12.4, dateInMonth(month, 18), 'Weekly groceries', month);
    addSpend('Gas', gas, dateInMonth(month, 8), 'Fill-up', month);
    addSpend('Fun', fun, dateInMonth(month, 12), 'Dinner out', month);

    const transferSource = createTransaction(
      'Fun',
      80,
      dateInMonth(month, 16),
      'Move leftover fun money',
    );
    addTransaction(findPond(envelopes, 'Fun'), transferSource, month);
    applyTransferGroup(envelopes, transferSource, 'Fun', [
      { bucketId: randomUUID(), toEnvelope: 'Savings', amount: 50 },
      { bucketId: randomUUID(), toEnvelope: 'Bills', amount: 30 },
    ]);

    applyGroup(envelopes, null, dateInMonth(month, 22), 'Warehouse run', null, [
      { bucketId: randomUUID(), pondName: 'Groceries', amount: 64.2 },
      { bucketId: randomUUID(), pondName: 'Fun', amount: 28.8 },
    ], month);

    const rent = createTransaction('Bills', 120, dateInMonth(month, 1), 'Rent');
    rent.recurring = true;
    rent.recurringFrequency = 'monthly';
    rent.recurringDays = [1];
    rent.recurringSeriesId = rentSeriesId;
    rent.recurringTemplate = index === 0;
    addTransaction(findPond(envelopes, 'Bills'), rent, month);
  });

  for (const envelope of envelopes) {
    for (const month of months) {
      rebuildMonthData(envelope, month);
    }
  }
  refreshBalances(profile, now);
  return profile;

  function addSpend(pondName, amount, date, comment, month) {
    const tx = createTransaction(pondName, amount, date, comment);
    addTransaction(findPond(envelopes, pondName), tx, month);
  }
}
