import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DOLLAR_SIGN, fromDefaults } from '../domain/ocrAmountWeights.js';
import { parse, ocrLine, ocrResult, ReceiptCaptureMode } from '../domain/receiptFieldParser.js';
import {
  readLearning,
  replaceLearningSnapshot,
  saveLearning,
  useLearningCollection,
} from '../server/learningStore.js';

function memoryLearningCollection() {
  const docs = new Map();
  return {
    docs,
    async findOne(query) {
      const doc = docs.get(String(query.userId));
      return doc ? { ...doc, comments: [...(doc.comments || [])], weights: doc.weights ? [...doc.weights] : doc.weights } : null;
    },
    async findOneAndUpdate(query, update, options = {}) {
      const userId = String(query.userId);
      let doc = docs.get(userId);
      if (!doc) {
        if (!options.upsert) return null;
        doc = { userId };
        docs.set(userId, doc);
      }
      if (update.$set) Object.assign(doc, update.$set);
      return doc;
    },
  };
}

function bindFresh() {
  const collection = memoryLearningCollection();
  useLearningCollection(collection);
  return collection;
}

test('a new user loads empty comments and default weights without creating a document', async () => {
  const collection = bindFresh();
  const learning = await readLearning('user-new');
  assert.deepEqual(learning.comments, []);
  assert.deepEqual(learning.weights, fromDefaults());
  assert.equal(collection.docs.size, 0);
  assert.deepEqual(Object.keys(learning).sort(), ['comments', 'weights']);
});

test('comments persist and a blank comment does not wipe the list', async () => {
  bindFresh();
  await saveLearning('user-a', { comment: 'Walmart' });
  await saveLearning('user-a', { comment: 'walmart' });
  await saveLearning('user-a', { comment: '   ' });
  const learning = await readLearning('user-a');
  assert.deepEqual(learning.comments, ['walmart']);
});

test('learned weights persist', async () => {
  bindFresh();
  const saved = await saveLearning('user-a', {
    comment: 'Kroger',
    ocrAmount: 45.12,
    savedAmount: 42,
    lines: ['Store', '$45.12', 'misc', '42.00'],
    mode: ReceiptCaptureMode.RECEIPT,
  });
  const again = await readLearning('user-a');
  assert.ok(saved.weights[DOLLAR_SIGN] < fromDefaults()[DOLLAR_SIGN]);
  assert.deepEqual(again.weights, saved.weights);
  assert.deepEqual(again.comments, ['Kroger']);
});

test('each user learning document is isolated', async () => {
  bindFresh();
  await saveLearning('user-a', { comment: 'Alpha' });
  await saveLearning('user-b', { comment: 'Beta' });
  assert.deepEqual((await readLearning('user-a')).comments, ['Alpha']);
  assert.deepEqual((await readLearning('user-b')).comments, ['Beta']);
});

test('ocr parsing receives the same five-number weights', async () => {
  bindFresh();
  const learning = await readLearning('user-a');
  assert.equal(learning.weights.length, 5);
  const draft = parse(
    ocrResult([ocrLine('Total $12.50', 0.9, 12)]),
    ReceiptCaptureMode.RECEIPT,
    learning.weights,
  );
  assert.equal(typeof draft.totalAmount, 'number');
});

test('learning remains after another read with no private cache', async () => {
  const collection = bindFresh();
  await saveLearning('user-a', { comment: 'Rent' });
  useLearningCollection(collection);
  const learning = await readLearning('user-a');
  assert.deepEqual(learning.comments, ['Rent']);
  assert.equal(collection.docs.size, 1);
});

test('a short stored weights array loads as defaults', async () => {
  const collection = bindFresh();
  collection.docs.set('user-a', { userId: 'user-a', comments: ['Keep'], weights: [1, 2] });
  const learning = await readLearning('user-a');
  assert.deepEqual(learning.comments, ['Keep']);
  assert.deepEqual(learning.weights, fromDefaults());
});

test('a snapshot without five weights keeps the stored weights', async () => {
  bindFresh();
  await saveLearning('user-a', { comment: 'Keep' });
  const before = await readLearning('user-a');
  const replaced = await replaceLearningSnapshot('user-a', {
    comments: [' Restored ', '', '  '],
    ocrWeights: [1, 2],
  });
  assert.deepEqual(replaced.comments, ['Restored']);
  assert.deepEqual(replaced.weights, before.weights);
  assert.deepEqual((await readLearning('user-a')).comments, ['Restored']);
});
