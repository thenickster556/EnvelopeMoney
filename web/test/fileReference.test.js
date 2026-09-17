import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  parseFileReference,
  serializeFileReference,
} from '../domain/fileReference.js';

test('parseFileReference reads GridFS /api/receipts ids', () => {
  const ref = parseFileReference('/api/receipts/64b1c2d3e4f5a6b7c8d9e0f1');
  assert.deepEqual(ref, { storage: 'gridfs', id: '64b1c2d3e4f5a6b7c8d9e0f1' });
});

test('parseFileReference reads local:// paths', () => {
  const ref = parseFileReference('local://receipts/2026-09/MountainMoney_1710000000000.jpg');
  assert.deepEqual(ref, {
    storage: 'local',
    path: 'receipts/2026-09/MountainMoney_1710000000000.jpg',
  });
});

test('parseFileReference keeps unknown strings without wiping them', () => {
  const ref = parseFileReference('content://legacy');
  assert.equal(ref.storage, 'unknown');
  assert.equal(ref.raw, 'content://legacy');
});

test('parseFileReference treats empty as unknown', () => {
  assert.equal(parseFileReference(null).storage, 'unknown');
  assert.equal(parseFileReference('').storage, 'unknown');
});

test('serializeFileReference round-trips gridfs and local', () => {
  assert.equal(
    serializeFileReference({ storage: 'gridfs', id: '64b1c2d3e4f5a6b7c8d9e0f1' }),
    '/api/receipts/64b1c2d3e4f5a6b7c8d9e0f1',
  );
  assert.equal(
    serializeFileReference({ storage: 'local', path: 'receipts/2026-09/walmart.jpg' }),
    'local://receipts/2026-09/walmart.jpg',
  );
});
