import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isIsoDateOutsideFilterRange } from '../domain/receiptDateFilter.js';

test('inside range is false', () => {
  assert.equal(isIsoDateOutsideFilterRange('2026-06-15', 'Jun 1, 2026', 'Jun 30, 2026'), false);
});

test('before start is true', () => {
  assert.equal(isIsoDateOutsideFilterRange('2024-03-15', 'Jun 1, 2026', 'Jun 30, 2026'), true);
});

test('after end is true', () => {
  assert.equal(isIsoDateOutsideFilterRange('2026-07-01', 'Jun 1, 2026', 'Jun 30, 2026'), true);
});

test('null or blank date is false', () => {
  assert.equal(isIsoDateOutsideFilterRange(null, 'Jun 1, 2026', 'Jun 30, 2026'), false);
  assert.equal(isIsoDateOutsideFilterRange('', 'Jun 1, 2026', 'Jun 30, 2026'), false);
});
