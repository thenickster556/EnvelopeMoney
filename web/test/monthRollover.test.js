import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createEnvelope, createTransaction, addTransaction, getMonthlyData } from '../domain/envelopeModel.js';
import { prepareForLaunch } from '../domain/monthRollover.js';

test('prepareForLaunch rolls forward with carry over', () => {
  const gas = createEnvelope('Gas', 100);
  gas.remaining = 35;
  gas.manualRemaining = null;
  addTransaction(gas, createTransaction('Gas', 65, '2026-02-12', 'Fuel'), '2026-02');
  const result = prepareForLaunch([gas], '2026-02', '2026-03', true);
  assert.equal(result.activeMonth, '2026-03');
  assert.equal(result.rolledOver, true);
  const rolled = result.envelopes[0];
  assert.equal(rolled.limit, 100);
  assert.equal(rolled.originalLimit, 100);
  assert.equal(rolled.remaining, 135);
  assert.equal(getMonthlyData(rolled, '2026-03').limit, 135);
});

test('prepareForLaunch repairs legacy transaction month', () => {
  const personal = createEnvelope('Personal', 200);
  const transaction = createTransaction('Personal', 10, '2026-02-10', 'Lunch');
  transaction.month = null;
  personal.transactions.push(transaction);
  const result = prepareForLaunch([personal], '2026-02', '2026-02', true);
  assert.equal(result.envelopes[0].transactions[0].month, '2026-02');
  assert.equal(getMonthlyData(result.envelopes[0], '2026-02').remaining, 190);
});

test('prepareForLaunch recovers malformed numeric state', () => {
  const outreach = createEnvelope('Outreach', 80);
  outreach.originalLimit = Number.NaN;
  outreach.remaining = Number.NaN;
  outreach.manualRemaining = Number.POSITIVE_INFINITY;
  const result = prepareForLaunch([outreach], 'not-a-month', '2026-03', true);
  const repaired = result.envelopes[0];
  assert.equal(result.activeMonth, '2026-03');
  assert.equal(Number.isFinite(repaired.originalLimit), true);
  assert.equal(Number.isFinite(repaired.remaining), true);
  assert.ok(getMonthlyData(repaired, '2026-03'));
});

test('prepareForLaunch is idempotent after successful rollover', () => {
  const vacation = createEnvelope('Vacation', 300);
  vacation.remaining = 250;
  const first = prepareForLaunch([vacation], '2026-02', '2026-03', true);
  const second = prepareForLaunch(first.envelopes, '2026-03', '2026-03', true);
  assert.equal(first.rolledOver, true);
  assert.equal(second.rolledOver, false);
  assert.equal(getMonthlyData(first.envelopes[0], '2026-03').limit, getMonthlyData(second.envelopes[0], '2026-03').limit);
  assert.equal(second.envelopes[0].limit, 300);
});
