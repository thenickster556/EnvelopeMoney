import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validate } from '../domain/splitPurchase.js';

test('validate rejects fewer than two slices', () => {
  const r = validate(10, [{ bucketId: 'a', pondName: 'Fun', amount: 10 }]);
  assert.equal(r.valid, false);
});

test('validate rejects duplicate ponds', () => {
  const r = validate(10, [
    { bucketId: '1', pondName: 'Fun', amount: 5 },
    { bucketId: '2', pondName: 'fun', amount: 5 },
  ]);
  assert.equal(r.valid, false);
});

test('validate rejects sum mismatch', () => {
  const r = validate(10, [
    { bucketId: '1', pondName: 'Fun', amount: 5 },
    { bucketId: '2', pondName: 'Edu', amount: 4 },
  ]);
  assert.equal(r.valid, false);
});

test('validate accepts exact sum', () => {
  const r = validate(10, [
    { bucketId: '1', pondName: 'Fun', amount: 5 },
    { bucketId: '2', pondName: 'Edu', amount: 5 },
  ]);
  assert.equal(r.valid, true);
});
