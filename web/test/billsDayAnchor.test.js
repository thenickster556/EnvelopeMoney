import { test } from 'node:test';
import assert from 'node:assert/strict';
import { computeAnchorDate } from '../domain/billsDayAnchor.js';
import { ymd } from '../domain/dates.js';

function cal(y, m0, d) {
  return new Date(y, m0, d);
}

test('empty or null returns null', () => {
  assert.equal(computeAnchorDate(new Date(), null), null);
  assert.equal(computeAnchorDate(new Date(), []), null);
});

test('single day today after bills day uses current month', () => {
  assert.equal(ymd(computeAnchorDate(cal(2026, 4, 15), [10])), '2026-05-10');
});

test('single day today before bills day walks prior month', () => {
  assert.equal(ymd(computeAnchorDate(cal(2026, 1, 10), [15])), '2026-01-15');
});

test('multi day today after first bills day', () => {
  assert.equal(ymd(computeAnchorDate(cal(2026, 3, 20), [10, 25])), '2026-04-10');
});

test('multi day no day yet in month', () => {
  assert.equal(ymd(computeAnchorDate(cal(2026, 3, 5), [10, 25])), '2026-03-25');
});

test('multi day today on period end uses prior bills day', () => {
  assert.equal(ymd(computeAnchorDate(cal(2026, 3, 15), [1, 15])), '2026-04-01');
});

test('single day today on bills day uses previous month', () => {
  assert.equal(ymd(computeAnchorDate(cal(2026, 3, 17), [17])), '2026-03-17');
});

test('february clamps 31st', () => {
  assert.equal(ymd(computeAnchorDate(cal(2026, 1, 15), [31])), '2026-01-31');
});

test('dom 31 clamps in shorter month', () => {
  assert.equal(ymd(computeAnchorDate(cal(2026, 2, 31), [31])), '2026-02-28');
});

test('single day day after bills uses current month', () => {
  assert.equal(ymd(computeAnchorDate(cal(2026, 4, 13), [12])), '2026-05-12');
});

test('single day before bills walks prior month', () => {
  assert.equal(ymd(computeAnchorDate(cal(2026, 4, 5), [12])), '2026-04-12');
});

test('single day later in month uses current month', () => {
  assert.equal(ymd(computeAnchorDate(cal(2026, 3, 20), [17])), '2026-04-17');
});
