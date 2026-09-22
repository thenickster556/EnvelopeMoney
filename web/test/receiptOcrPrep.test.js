import { test } from 'node:test';
import assert from 'node:assert/strict';
import { scaledSize, fillEmptyOcrFields, createOcrSession, OCR_MAX_EDGE } from '../domain/receiptOcrPrep.js';

test('scaledSize keeps small images and caps the long edge at 1280', () => {
  assert.equal(OCR_MAX_EDGE, 1280);
  assert.deepEqual(scaledSize(800, 600), { width: 800, height: 600 });
  assert.deepEqual(scaledSize(2560, 1280), { width: 1280, height: 640 });
  assert.deepEqual(scaledSize(1000, 4000), { width: 320, height: 1280 });
  assert.deepEqual(scaledSize(0, 100), { width: 0, height: 0 });
});

test('fillEmptyOcrFields fills an empty comment and leaves a typed comment', () => {
  const draft = { totalAmount: 21.66, dateYyyyMmDd: '2026-09-22', merchantForComment: 'Walmart' };
  const filled = fillEmptyOcrFields({ amount: '', date: '2026-09-01', comment: '' }, draft);
  assert.equal(filled.amount, 21.66);
  assert.equal(filled.date, '2026-09-22');
  assert.equal(filled.comment, 'Walmart');

  const kept = fillEmptyOcrFields({ amount: '9.00', date: '2026-09-01', comment: 'Lunch' }, draft);
  assert.equal(kept.amount, '9.00');
  assert.equal(kept.comment, 'Lunch');
  assert.equal(kept.date, '2026-09-22');
});

test('createOcrSession reuses one worker', async () => {
  let created = 0;
  const session = createOcrSession(async () => {
    created += 1;
    return { recognize: async (image) => ({ data: { lines: [], image } }) };
  });
  await session.recognize('a');
  await session.recognize('b');
  assert.equal(created, 1);
});
