import initSqlJs from 'sql.js';
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  createSchema,
  loadComments,
  loadWeights,
  rememberComment,
  saveWeights,
} from '../domain/learningDb.js';
import { learn } from '../domain/ocrAmountLearner.js';

const require = createRequire(import.meta.url);
const wasmPath = join(dirname(require.resolve('sql.js')), 'sql-wasm.wasm');
const defaultDataDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'data');

let sqlPromise;

function getSql() {
  if (!sqlPromise) {
    sqlPromise = initSqlJs({ locateFile: () => wasmPath });
  }
  return sqlPromise;
}

function safeUserId(userId) {
  return String(userId || 'anon').replace(/[^a-zA-Z0-9_-]/g, '_');
}

export async function withUserLearningDb(userId, fn, dataDir = defaultDataDir) {
  const SQL = await getSql();
  const dir = join(dataDir, 'learning');
  mkdirSync(dir, { recursive: true });
  const filePath = join(dir, `${safeUserId(userId)}.db`);
  const db = existsSync(filePath)
    ? new SQL.Database(readFileSync(filePath))
    : new SQL.Database();
  try {
    createSchema(db);
    const result = await fn(db);
    writeFileSync(filePath, Buffer.from(db.export()));
    return result;
  } finally {
    db.close();
  }
}

export async function readLearning(userId) {
  return withUserLearningDb(userId, (db) => ({
    comments: loadComments(db),
    weights: loadWeights(db),
  }));
}

export async function saveLearning(userId, body) {
  return withUserLearningDb(userId, (db) => {
    const comments = rememberComment(db, body && body.comment, Date.now());
    let weights = loadWeights(db);
    const ocrAmount = body && body.ocrAmount;
    const savedAmount = body && body.savedAmount;
    const lines = body && Array.isArray(body.lines) ? body.lines : [];
    const mode = body && body.mode;
    if (ocrAmount != null && Number.isFinite(Number(savedAmount)) && lines.length) {
      weights = learn(lines, Number(ocrAmount), Number(savedAmount), weights, mode);
      saveWeights(db, weights);
    }
    return { comments, weights };
  });
}
