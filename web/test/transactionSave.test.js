import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createEnvelope, getTransactions } from '../domain/envelopeModel.js';
import { saveSpendingOrTransfer } from '../domain/transactionSave.js';

function ponds() {
  return [
    createEnvelope('Fun', 100),
    createEnvelope('Groceries', 100),
    createEnvelope('Savings', 0),
  ];
}

function findPond(envelopes, name) {
  return envelopes.find((env) => env.name === name);
}

function validBuckets() {
  return [{ bucketId: 'a', toEnvelope: 'Savings', amount: 50 }];
}

function transferInput(overrides) {
  return {
    source: null,
    existing: null,
    isEdit: false,
    type: 'transfer',
    pondName: 'Fun',
    amount: 50,
    date: '2026-09-15',
    comment: 'Move',
    receiptUri: null,
    month: '2026-09',
    buckets: validBuckets(),
    ...overrides,
  };
}

test('invalid transfer leaves pond rows unchanged', () => {
  const envelopes = ponds();
  const result = saveSpendingOrTransfer(envelopes, transferInput({ buckets: [] }));
  assert.equal(result.ok, false);
  assert.equal(getTransactions(findPond(envelopes, 'Fun')).length, 0);
  assert.equal(getTransactions(findPond(envelopes, 'Savings')).length, 0);
});

test('retry once valid inserts a single source', () => {
  const envelopes = ponds();
  const first = saveSpendingOrTransfer(envelopes, transferInput({ buckets: [] }));
  assert.equal(first.ok, false);
  const second = saveSpendingOrTransfer(envelopes, transferInput());
  assert.equal(second.ok, true);
  const funRows = getTransactions(findPond(envelopes, 'Fun'));
  const savingsRows = getTransactions(findPond(envelopes, 'Savings'));
  assert.equal(funRows.length, 1);
  assert.equal(funRows[0], second.tx);
  assert.equal(funRows[0].transferId != null, true);
  assert.equal(funRows[0].transferBucketId == null, true);
  assert.equal(savingsRows.length, 1);
  assert.equal(savingsRows[0].transferId, second.tx.transferId);
});

test('edit transfer moves the source pond Fun to Groceries', () => {
  const envelopes = ponds();
  const created = saveSpendingOrTransfer(envelopes, transferInput());
  assert.equal(created.ok, true);
  const source = created.tx;
  const moved = saveSpendingOrTransfer(envelopes, transferInput({
    source,
    existing: source,
    isEdit: true,
    pondName: 'Groceries',
  }));
  assert.equal(moved.ok, true);
  assert.equal(moved.tx, source);
  const funRows = getTransactions(findPond(envelopes, 'Fun'));
  const groceryRows = getTransactions(findPond(envelopes, 'Groceries'));
  const savingsRows = getTransactions(findPond(envelopes, 'Savings'));
  assert.equal(funRows.includes(source), false);
  assert.equal(groceryRows.includes(source), true);
  assert.equal(source.envelopeName, 'Groceries');
  assert.equal(savingsRows.length, 1);
  assert.equal(savingsRows[0].transferId, source.transferId);
  assert.match(savingsRows[0].comment, /Groceries/);
});
