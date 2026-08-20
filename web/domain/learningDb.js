import { remember } from './commentHistory.js';
import {
  FEATURE_COUNT,
  encodeWeights,
  decodeWeights,
  fromDefaults,
  clampWeights,
  copyOrDefault,
} from './ocrAmountWeights.js';

export function createSchema(db) {
  db.run('CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)');
  db.run('CREATE TABLE IF NOT EXISTS comments (text TEXT PRIMARY KEY, last_used_ms INTEGER NOT NULL)');
  db.run('CREATE TABLE IF NOT EXISTS ocr_weight_vec (id INTEGER PRIMARY KEY CHECK (id = 1), n INTEGER, floats BLOB)');
  db.run("INSERT OR IGNORE INTO meta(key, value) VALUES ('version', '1')");
  const existing = db.exec('SELECT id FROM ocr_weight_vec WHERE id = 1');
  if (!existing.length || !existing[0].values.length) {
    db.run('INSERT INTO ocr_weight_vec (id, n, floats) VALUES (?, ?, ?)', [
      1,
      FEATURE_COUNT,
      encodeWeights(fromDefaults()),
    ]);
  }
}

export function loadComments(db) {
  const rows = [];
  const stmt = db.prepare('SELECT text FROM comments ORDER BY last_used_ms DESC');
  while (stmt.step()) {
    rows.push(stmt.get()[0]);
  }
  stmt.free();
  return rows;
}

export function rememberComment(db, text, nowMs) {
  const next = remember(loadComments(db), text);
  db.run('DELETE FROM comments');
  let stamp = nowMs != null ? nowMs : Date.now();
  for (const comment of next) {
    db.run('INSERT INTO comments (text, last_used_ms) VALUES (?, ?)', [comment, stamp]);
    stamp -= 1;
  }
  return next;
}

export function loadWeights(db) {
  const stmt = db.prepare('SELECT n, floats FROM ocr_weight_vec WHERE id = 1');
  try {
    if (stmt.step()) {
      const n = stmt.get()[0];
      const blob = stmt.get()[1];
      if (n === FEATURE_COUNT) {
        const bytes = blob instanceof Uint8Array ? blob : new Uint8Array(blob || []);
        return decodeWeights(bytes);
      }
    }
  } finally {
    stmt.free();
  }
  return fromDefaults();
}

export function saveWeights(db, weights) {
  const clamped = clampWeights(copyOrDefault(weights));
  db.run('INSERT OR REPLACE INTO ocr_weight_vec (id, n, floats) VALUES (?, ?, ?)', [
    1,
    FEATURE_COUNT,
    encodeWeights(clamped),
  ]);
}
