import { test } from 'node:test';
import assert from 'node:assert/strict';
import { allocatedTotal, spentInSource, validate } from '../domain/transferGroup.js';

test('allocated total and spent in source', () => {
  const alloc = [
    { bucketId: 'a', toEnvelope: 'Savings', amount: 8 },
    { bucketId: 'b', toEnvelope: 'Vacation', amount: 4.5 },
  ];
  assert.equal(allocatedTotal(alloc), 12.5);
  assert.equal(spentInSource(20, alloc), 7.5);
});

test('validate rejects duplicate destinations', () => {
  const result = validate(20, 'Groceries', [
    { bucketId: 'a', toEnvelope: 'Savings', amount: 8 },
    { bucketId: 'b', toEnvelope: 'Savings', amount: 4.5 },
  ]);
  assert.equal(result.valid, false);
  assert.equal(result.message, 'Each destination can only appear once per transfer');
});

test('validate rejects over allocation', () => {
  const result = validate(10, 'Groceries', [
    { bucketId: 'a', toEnvelope: 'Savings', amount: 8 },
    { bucketId: 'b', toEnvelope: 'Vacation', amount: 4.5 },
  ]);
  assert.equal(result.valid, false);
  assert.match(result.message, /Transfers exceed the total/);
});

test('validate accepts unique positive allocations', () => {
  const result = validate(20, 'Groceries', [
    { bucketId: 'a', toEnvelope: 'Savings', amount: 8 },
    { bucketId: 'b', toEnvelope: 'Vacation', amount: 4.5 },
  ]);
  assert.equal(result.valid, true);
});

test('validate requires at least one bucket', () => {
  const result = validate(20, 'Groceries', []);
  assert.equal(result.valid, false);
  assert.equal(result.message, 'Add at least one transfer bucket');
});
