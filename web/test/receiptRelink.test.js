import { test } from 'node:test';
import assert from 'node:assert/strict';
import { relinkLocalReceipts, receiptNeedsAttention } from '../domain/receiptRelink.js';

const FILE = 'MountainMoney_1710000000000.jpg';

test('unique filename in the folder becomes a local reference', () => {
  const envelopes = [{
    name: 'Groceries',
    transactions: [{
      amount: 12.5,
      receiptImageUri: 'content://media/external/images/media/5',
      receiptImageFileName: FILE,
    }],
    monthlyData: {
      '2026-08': {
        transactions: [{
          receiptImageUri: '/api/receipts/aaaaaaaaaaaaaaaaaaaaaaaa',
          receiptImageFileName: 'older.jpg',
        }],
      },
    },
  }];
  const files = [
    { path: `receipts/2026-09/${FILE}`, name: FILE },
    { path: 'receipts/2026-08/older.jpg', name: 'older.jpg' },
  ];
  const result = relinkLocalReceipts(envelopes, files);
  assert.equal(result.changed, 2);
  assert.equal(result.envelopes[0].transactions[0].receiptImageUri, `local://receipts/2026-09/${FILE}`);
  assert.equal(result.envelopes[0].monthlyData['2026-08'].transactions[0].receiptImageUri, 'local://receipts/2026-08/older.jpg');
  assert.equal(envelopes[0].transactions[0].receiptImageUri, 'content://media/external/images/media/5');
});

test('a missing file and a duplicate name leave the stored pointer alone', () => {
  const envelopes = [{
    transactions: [
      { receiptImageUri: 'content://old/1', receiptImageFileName: 'gone.jpg' },
      { receiptImageUri: 'content://old/2', receiptImageFileName: 'dup.jpg' },
      { receiptImageUri: 'local://receipts/same.jpg', receiptImageFileName: 'same.jpg' },
    ],
  }];
  const result = relinkLocalReceipts(envelopes, [
    { path: 'a/dup.jpg', name: 'dup.jpg' },
    { path: 'b/dup.jpg', name: 'dup.jpg' },
    { path: 'receipts/same.jpg', name: 'same.jpg' },
  ]);
  assert.equal(result.changed, 0);
  assert.equal(result.envelopes[0].transactions[0].receiptImageUri, 'content://old/1');
  assert.equal(result.envelopes[0].transactions[1].receiptImageUri, 'content://old/2');
});

test('receipt attention follows whether this device can open the picture', () => {
  assert.equal(receiptNeedsAttention({ uri: 'local://receipts/a.jpg', mode: 'local', localExists: false }), true);
  assert.equal(receiptNeedsAttention({ uri: 'local://receipts/a.jpg', mode: 'local', localExists: true }), false);
  assert.equal(receiptNeedsAttention({ uri: 'content://media/1', mode: 'local', localExists: false }), true);
  assert.equal(receiptNeedsAttention({
    uri: '/api/receipts/aaaaaaaaaaaaaaaaaaaaaaaa',
    mode: 'local',
    localExists: false,
  }), true);
  assert.equal(receiptNeedsAttention({
    uri: '/api/receipts/aaaaaaaaaaaaaaaaaaaaaaaa',
    mode: 'server',
    localExists: false,
  }), false);
  assert.equal(receiptNeedsAttention({ uri: '', mode: 'local', localExists: false }), false);
});
