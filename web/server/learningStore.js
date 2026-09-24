import { getDb } from './db.js';
import { remember, MAX_COMMENTS } from '../domain/commentHistory.js';
import { learn } from '../domain/ocrAmountLearner.js';
import { clampWeights, copyOrDefault } from '../domain/ocrAmountWeights.js';

let collectionOverride = null;

/** Test hook. Production uses the Mongo `learning` collection. */
export function useLearningCollection(collection) {
  collectionOverride = collection || null;
}

function learningCollection() {
  if (collectionOverride) return collectionOverride;
  return getDb().collection('learning');
}

function publicLearning(doc) {
  const comments = doc && Array.isArray(doc.comments) ? doc.comments.filter((c) => typeof c === 'string') : [];
  return {
    comments,
    weights: copyOrDefault(doc && doc.weights),
  };
}

function restoredComments(list) {
  const kept = [];
  const source = Array.isArray(list) ? list : [];
  for (const comment of source) {
    if (kept.length >= MAX_COMMENTS) break;
    if (typeof comment !== 'string') continue;
    const text = comment.trim();
    if (!text) continue;
    kept.push(text);
  }
  return kept;
}

async function upsertLearning(userId, comments, weights) {
  const col = learningCollection();
  const id = String(userId);
  const update = {
    $set: {
      comments,
      weights,
      updatedAt: new Date(),
    },
    $setOnInsert: { userId: id },
  };
  const options = { upsert: true, returnDocument: 'after' };
  try {
    await col.findOneAndUpdate({ userId: id }, update, options);
  } catch (err) {
    if (err && err.code === 11000) {
      await col.findOneAndUpdate({ userId: id }, { $set: update.$set }, { returnDocument: 'after' });
      return;
    }
    throw err;
  }
}

export async function readLearning(userId) {
  const doc = await learningCollection().findOne({ userId: String(userId) });
  return publicLearning(doc);
}

/** Replace comment history and OCR weights from a budget file. Omits weights that are not 5 numbers. */
export async function replaceLearningSnapshot(userId, learning) {
  const current = await readLearning(userId);
  const comments = restoredComments(learning && learning.comments);
  const incoming = learning && Array.isArray(learning.ocrWeights) ? learning.ocrWeights : null;
  const weights = incoming && incoming.length === 5
    ? clampWeights(incoming)
    : current.weights;
  await upsertLearning(userId, comments, weights);
  return { comments, weights };
}

export async function saveLearning(userId, body) {
  const current = await readLearning(userId);
  const comments = remember(current.comments, body && body.comment);
  let weights = current.weights;
  const ocrAmount = body && body.ocrAmount;
  const savedAmount = body && body.savedAmount;
  const lines = body && Array.isArray(body.lines) ? body.lines : [];
  const mode = body && body.mode;
  if (ocrAmount != null && Number.isFinite(Number(savedAmount)) && lines.length) {
    weights = learn(lines, Number(ocrAmount), Number(savedAmount), weights, mode);
  }
  weights = clampWeights(weights);
  await upsertLearning(userId, comments, weights);
  return { comments, weights };
}
