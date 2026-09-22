import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fillEmptyOcrFields, createOcrSession, ocrSourceImage } from '../domain/receiptOcrPrep.js';

test('ocrSourceImage keeps the original photo so Tesseract sees full detail', () => {
  const photo = { name: 'receipt.jpg', size: 4_000_000 };
  assert.equal(ocrSourceImage(photo), photo);
  assert.equal(ocrSourceImage(null), null);
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

test('createOcrSession reuses one worker and recognizes the original image', async () => {
  let created = 0;
  const seen = [];
  const session = createOcrSession(async () => {
    created += 1;
    return {
      recognize: async (image) => {
        seen.push(image);
        return { data: { lines: [], image } };
      },
    };
  });
  const photo = { name: 'receipt.jpg' };
  await session.recognize(ocrSourceImage(photo));
  await session.recognize(ocrSourceImage(photo));
  assert.equal(created, 1);
  assert.equal(seen[0], photo);
  assert.equal(seen[1], photo);
});
