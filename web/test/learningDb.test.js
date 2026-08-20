import { test } from 'node:test';
import assert from 'node:assert/strict';
import initSqlJs from 'sql.js';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import {
  createSchema,
  loadComments,
  loadWeights,
  rememberComment,
  saveWeights,
} from '../domain/learningDb.js';
import { DEFAULTS } from '../domain/ocrAmountWeights.js';

const require = createRequire(import.meta.url);
const wasmPath = join(dirname(require.resolve('sql.js')), 'sql-wasm.wasm');

async function openMemory() {
  const SQL = await initSqlJs({ locateFile: () => wasmPath });
  const db = new SQL.Database();
  createSchema(db);
  return db;
}

test('learning db comment upsert and recency', async () => {
  const db = await openMemory();
  rememberComment(db, 'fill-up', 1000);
  rememberComment(db, 'Coffee', 2000);
  rememberComment(db, 'Fill-up', 3000);
  assert.deepEqual(loadComments(db), ['Fill-up', 'Coffee']);
  db.close();
});

test('learning db weight blob round trip and corrupt fallback', async () => {
  const db = await openMemory();
  assert.deepEqual(loadWeights(db), DEFAULTS);
  const updated = [25, 80, 60, 25, -100];
  saveWeights(db, updated);
  assert.deepEqual(loadWeights(db), updated);
  db.run('UPDATE ocr_weight_vec SET floats = x\'010203\' WHERE id = 1');
  assert.deepEqual(loadWeights(db), DEFAULTS);
  db.close();
});
