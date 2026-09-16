import { test } from 'node:test';
import assert from 'node:assert/strict';
import { compute, resolvePaydayDaysInMonth } from '../domain/pondBankReconciliation.js';
import { roundToCents } from '../domain/moneyMath.js';

function cal(y, m0, d) {
  return new Date(y, m0, d);
}

function monthStart(y, m0) {
  return cal(y, m0, 1);
}

test('before first payday still equals limit remaining is account minus spend', () => {
  const r = compute(100, 40, [15, 30], monthStart(2026, 4), cal(2026, 4, 10), 5);
  assert.equal(r.active, true);
  assert.equal(r.paydaysPassed, 0);
  assert.equal(r.stillToDepositForMonth, 100);
  assert.equal(r.unlockedFromPaydays, 0);
  assert.equal(r.estimatedRemaining, 35);
  assert.equal(r.monthTarget, 100);
  assert.equal(r.inBank, 40);
});

test('after first of two unlocked half', () => {
  const r = compute(100, 40, [1, 15], monthStart(2026, 4), cal(2026, 4, 13), 20);
  assert.equal(r.paydaysPassed, 1);
  assert.equal(r.paydaysInMonth, 2);
  assert.equal(r.perPayday, 50);
  assert.equal(r.unlockedFromPaydays, 50);
  assert.equal(r.stillToDepositForMonth, 50);
  assert.equal(r.estimatedRemaining, 70);
});

test('after all paydays still zero', () => {
  const r = compute(100, 40, [1, 15], monthStart(2026, 4), cal(2026, 4, 20), 20);
  assert.equal(r.paydaysPassed, 2);
  assert.equal(r.stillToDepositForMonth, 0);
  assert.equal(r.unlockedFromPaydays, 100);
  assert.equal(r.estimatedRemaining, 120);
});

test('past month all paydays passed', () => {
  const r = compute(100, 40, [1, 15], monthStart(2026, 3), cal(2026, 4, 10), 10);
  assert.equal(r.paydaysPassed, 2);
  assert.equal(r.stillToDepositForMonth, 0);
  assert.equal(r.unlockedFromPaydays, 100);
  assert.equal(r.estimatedRemaining, 130);
});

test('future month no paydays passed', () => {
  const r = compute(100, 40, [1, 15], monthStart(2026, 5), cal(2026, 4, 10), 0);
  assert.equal(r.paydaysPassed, 0);
  assert.equal(r.stillToDepositForMonth, 100);
  assert.equal(r.estimatedRemaining, 40);
});

test('spending changes remaining limit unchanged', () => {
  const low = compute(100, 40, [1, 15], monthStart(2026, 4), cal(2026, 4, 13), 5);
  const high = compute(100, 40, [1, 15], monthStart(2026, 4), cal(2026, 4, 13), 25);
  assert.equal(low.monthTarget, 100);
  assert.equal(high.monthTarget, 100);
  assert.equal(low.estimatedRemaining, 85);
  assert.equal(high.estimatedRemaining, 65);
});

test('payday shares sum to limit', () => {
  const r = compute(100, 0, [1, 15, 30], monthStart(2026, 4), cal(2026, 4, 20), 0);
  assert.equal(r.paydaysInMonth, 3);
  assert.equal(roundToCents(r.unlockedFromPaydays + r.stillToDepositForMonth), 100);
});

test('null account inactive', () => {
  const r = compute(100, null, [1, 15], monthStart(2026, 4), cal(2026, 4, 13), 0);
  assert.equal(r.active, false);
  assert.equal(r.estimatedRemaining, 0);
});

test('day 31 in february clamps', () => {
  const resolved = resolvePaydayDaysInMonth([31], monthStart(2026, 1));
  assert.equal(resolved.length, 1);
  assert.equal(resolved[0], 28);
});

test('duplicate clamped paydays deduped', () => {
  const resolved = resolvePaydayDaysInMonth([31, 31], monthStart(2026, 1));
  assert.equal(resolved.length, 1);
});

test('negative remaining when overspend', () => {
  const r = compute(100, 10, [1], monthStart(2026, 4), cal(2026, 4, 20), 150);
  assert.equal(r.paydaysPassed, 1);
  assert.equal(r.stillToDepositForMonth, 0);
  assert.equal(r.estimatedRemaining, -40);
});
