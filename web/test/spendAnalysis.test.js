import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createEnvelope, createTransaction } from '../domain/envelopeModel.js';
import {
  analyze,
  barScaleMax,
  countsAsSpend,
  monthKeys,
  normalizeLastN,
} from '../domain/spendAnalysis.js';

function tx(pond, amount, date, extras = {}) {
  const row = createTransaction(pond, amount, date, 'note');
  Object.assign(row, extras);
  return row;
}

function pond(name, limit, transactions) {
  const envelope = createEnvelope(name, limit);
  envelope.transactions = transactions;
  return envelope;
}

function query(endMonth, lastNMonths, pondNames, includeTransfers) {
  return { endMonth, lastNMonths, pondNames, includeTransfers };
}

test('monthKeys last 3 from August', () => {
  assert.deepEqual(monthKeys('2026-08', 3), ['2026-06', '2026-07', '2026-08']);
});

test('monthKeys last 12 wraps year', () => {
  const keys = monthKeys('2026-02', 12);
  assert.equal(keys.length, 12);
  assert.equal(keys[0], '2025-03');
  assert.equal(keys[11], '2026-02');
});

test('invalid lastN defaults to 3', () => {
  assert.equal(monthKeys('2026-08', 4).length, 3);
  assert.equal(normalizeLastN(0), 3);
  assert.equal(normalizeLastN(6), 6);
  assert.equal(normalizeLastN(12), 12);
});

test('countsAsSpend excludes transfer source and mirrors when off', () => {
  const spending = tx('Groceries', 42, '2026-08-03');
  const source = tx('Fun', 100, '2026-08-01', { transferId: 'tid' });
  const mirror = tx('Savings', -40, '2026-08-01', { transferId: 'tid', transferBucketId: 'b1' });
  const split = tx('Gas', 12, '2026-08-02', { splitPurchaseGroupId: 'split-1' });

  assert.equal(countsAsSpend(spending, false), true);
  assert.equal(countsAsSpend(source, false), false);
  assert.equal(countsAsSpend(mirror, false), false);
  assert.equal(countsAsSpend(split, false), true);
  assert.equal(countsAsSpend(source, true), true);
  assert.equal(countsAsSpend(mirror, true), true);
});

test('analyze excludes transfers by default and always counts splits', () => {
  const result = analyze([
    pond('Groceries', 500, [
      tx('Groceries', 80, '2026-06-02'),
      tx('Groceries', 90, '2026-07-02'),
      tx('Groceries', 100, '2026-08-02'),
    ]),
    pond('Fun', 250, [tx('Fun', 40, '2026-08-01', { transferId: 'tid' })]),
    pond('Savings', 300, [tx('Savings', -40, '2026-08-01', { transferId: 'tid', transferBucketId: 'b1' })]),
    pond('Gas', 200, [tx('Gas', 15, '2026-08-04', { splitPurchaseGroupId: 'split-1' })]),
  ], query('2026-08', 3, [], false));

  assert.equal(result.months.length, 3);
  assert.equal(result.months[0].month, '2026-06');
  assert.equal(result.months[0].totalSpend, 80);
  assert.equal(result.months[1].totalSpend, 90);
  assert.equal(result.months[2].totalSpend, 115);
  assert.equal(result.months[2].totalLimit, 1250);
});

test('analyze includeTransfers matches all amounts', () => {
  const envelopes = [
    pond('Fun', 250, [
      tx('Fun', 50, '2026-08-02'),
      tx('Fun', 100, '2026-08-01', { transferId: 'tid' }),
    ]),
    pond('Savings', 300, [tx('Savings', -40, '2026-08-01', { transferId: 'tid', transferBucketId: 'b1' })]),
  ];
  const off = analyze(envelopes, query('2026-08', 3, [], false));
  const on = analyze(envelopes, query('2026-08', 3, [], true));
  assert.equal(off.months[2].totalSpend, 50);
  assert.equal(on.months[2].totalSpend, 110);
  assert.equal(on.byPond.find((p) => p.pondName === 'Fun').spend, 150);
  assert.equal(on.byPond.find((p) => p.pondName === 'Savings').spend, -40);
});

test('over budget only when spend exceeds Envelope.limit', () => {
  const result = analyze([
    pond('Gas', 200, [
      tx('Gas', 200, '2026-07-01'),
      tx('Gas', 210, '2026-08-01'),
    ]),
    pond('Groceries', 500, [tx('Groceries', 500, '2026-08-01')]),
  ], query('2026-08', 3, [], false));

  assert.equal(result.overBudget.length, 1);
  assert.equal(result.overBudget[0].month, '2026-08');
  assert.equal(result.overBudget[0].pondName, 'Gas');
  assert.equal(result.overBudget[0].spend, 210);
  assert.equal(result.overBudget[0].limit, 200);
  assert.equal(result.overBudget[0].overBy, 10);
});

test('empty ponds still emit zero months', () => {
  const result = analyze([pond('Fun', 250, [])], query('2026-08', 3, [], false));
  assert.equal(result.months.length, 3);
  assert.equal(result.months[0].totalSpend, 0);
  assert.equal(result.months[2].totalLimit, 250);
  assert.equal(result.overBudget.length, 0);
  assert.equal(result.hasSpendInRange, false);
});

test('pond filter uses current envelope name', () => {
  const result = analyze([
    pond('Groceries', 500, [tx('Groceries', 40, '2026-08-01')]),
    pond('Gas', 200, [tx('Gas', 90, '2026-08-01')]),
  ], query('2026-08', 3, ['Groceries'], false));
  assert.equal(result.months[2].totalSpend, 40);
  assert.equal(result.months[2].totalLimit, 500);
  assert.equal(result.byPond.length, 1);
  assert.equal(result.thisMonth[0].pondName, 'Groceries');
});

test('over budget sorted by overBy then month desc', () => {
  const result = analyze([
    pond('Gas', 100, [
      tx('Gas', 130, '2026-07-01'),
      tx('Gas', 110, '2026-08-01'),
    ]),
    pond('Fun', 50, [tx('Fun', 80, '2026-08-01')]),
  ], query('2026-08', 3, [], false));
  assert.equal(result.overBudget.length, 3);
  assert.equal(result.overBudget[0].pondName, 'Fun');
  assert.equal(result.overBudget[0].overBy, 30);
  assert.equal(result.overBudget[1].pondName, 'Gas');
  assert.equal(result.overBudget[1].month, '2026-07');
  assert.equal(result.overBudget[2].month, '2026-08');
});

test('snapshot uses remaining and Envelope.limit not MonthData.limit', () => {
  const gas = pond('Gas', 200, [tx('Gas', 210, '2026-08-01')]);
  gas.monthlyData['2026-08'] = { limit: 999, remaining: 0, transactions: [] };
  gas.remaining = -10;
  const result = analyze([gas], query('2026-08', 3, [], false));
  assert.equal(result.thisMonth[0].spend, 210);
  assert.equal(result.thisMonth[0].limit, 200);
  assert.equal(result.thisMonth[0].remaining, -10);
  assert.equal(result.thisMonth[0].overBudget, true);
});

test('barScaleMax never zero', () => {
  assert.equal(barScaleMax([0]), 0.01);
  assert.equal(barScaleMax([1, 12.34, 0]), 12.34);
});
