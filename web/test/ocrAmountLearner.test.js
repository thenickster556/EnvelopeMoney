import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULTS,
  DOLLAR_SIGN,
  decodeWeights,
  encodeWeights,
  fromDefaults,
} from '../domain/ocrAmountWeights.js';
import { learn } from '../domain/ocrAmountLearner.js';
import { ReceiptCaptureMode } from '../domain/receiptFieldParser.js';

const TWO_TOTAL = ['Store', '$45.12', 'misc', '42.00'];

test('defaults match parser constants', () => {
  assert.deepEqual(fromDefaults(), [50, 80, 60, 25, -100]);
});

test('little endian blob round trip', () => {
  const blob = encodeWeights(DEFAULTS);
  assert.equal(blob.length, 20);
  assert.deepEqual(decodeWeights(blob), DEFAULTS);
});

test('corrupt blob returns defaults', () => {
  assert.deepEqual(decodeWeights(null), DEFAULTS);
  assert.deepEqual(decodeWeights(new Uint8Array([1, 2, 3])), DEFAULTS);
});

test('learn bumps differing features and clamps', () => {
  const next = learn(TWO_TOTAL, 45.12, 42.00, DEFAULTS, ReceiptCaptureMode.RECEIPT);
  assert.ok(next[DOLLAR_SIGN] < DEFAULTS[DOLLAR_SIGN]);
  const clamped = learn(TWO_TOTAL, 45.12, 42.00, [5, 80, 60, 25, -100], ReceiptCaptureMode.RECEIPT);
  assert.equal(clamped[DOLLAR_SIGN], 0);
});

test('learn skips when saved amount is not a candidate', () => {
  assert.deepEqual(learn(TWO_TOTAL, 45.12, 99.99, DEFAULTS, ReceiptCaptureMode.RECEIPT), DEFAULTS);
});

test('learn skips when amounts match', () => {
  assert.deepEqual(learn(TWO_TOTAL, 45.12, 45.12, DEFAULTS, ReceiptCaptureMode.RECEIPT), DEFAULTS);
});
