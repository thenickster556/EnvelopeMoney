import { test } from 'node:test';
import assert from 'node:assert/strict';
import { roundToCents, splitIntegerPercentsFirstCeiling, splitTotalByPercents } from '../domain/moneyMath.js';

test('roundToCents half up', () => {
  assert.equal(roundToCents(100.0 / 3), 33.33);
  assert.equal(roundToCents(50.004), 50.00);
  assert.equal(roundToCents(50.005), 50.01);
});

test('splitIntegerPercentsFirstCeiling examples', () => {
  assert.deepEqual(splitIntegerPercentsFirstCeiling(1), [100]);
  assert.deepEqual(splitIntegerPercentsFirstCeiling(2), [50, 50]);
  assert.deepEqual(splitIntegerPercentsFirstCeiling(3), [34, 33, 33]);
  assert.deepEqual(splitIntegerPercentsFirstCeiling(5), [20, 20, 20, 20, 20]);
});

test('splitTotalByPercents sums to total', () => {
  const three = splitTotalByPercents(100.00, splitIntegerPercentsFirstCeiling(3));
  assertAmountsSum(100.00, three);
  const parts = splitTotalByPercents(100.00, [34, 33, 33]);
  assert.equal(parts[0], 34.00);
  assert.equal(parts[1], 33.00);
  assert.equal(parts[2], 33.00);
  assertAmountsSum(27.50, splitTotalByPercents(27.50, splitIntegerPercentsFirstCeiling(3)));
});

test('splitTotalByPercents tiny total', () => {
  assertAmountsSum(0.01, splitTotalByPercents(0.01, splitIntegerPercentsFirstCeiling(2)));
});

function assertAmountsSum(expectedTotal, amounts) {
  let sum = 0;
  for (const amount of amounts) sum += amount;
  assert.ok(Math.abs(roundToCents(sum) - expectedTotal) < 0.001);
}
