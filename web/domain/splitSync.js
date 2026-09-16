import { createTransaction, addTransaction, getTransactions } from './envelopeModel.js';
import { allocatedTotal as draftAllocated } from './splitPurchase.js';
import { randomUUID } from './id.js';

function findEnvelopeByName(envelopes, envelopeName) {
  if (!envelopes || envelopeName == null) return null;
  for (const envelope of envelopes) {
    if (envelope && envelope.name === envelopeName) return envelope;
  }
  return null;
}

export function isSplitPurchase(t) {
  if (!t) return false;
  return !!(t.splitPurchaseGroupId);
}

export function findTransactionsInGroup(envelopes, groupId) {
  const out = [];
  if (!groupId || !envelopes) return out;
  for (const e of envelopes) {
    for (const t of getTransactions(e)) {
      if (t.splitPurchaseGroupId === groupId) out.push(t);
    }
  }
  return out;
}

export function toAllocations(groupTransactions) {
  const slices = [];
  if (!groupTransactions) return slices;
  for (const t of groupTransactions) {
    if (!t || !isSplitPurchase(t)) continue;
    let bid = t.splitPurchaseBucketId;
    if (!bid) bid = randomUUID();
    slices.push({ bucketId: bid, pondName: t.envelopeName, amount: t.amount });
  }
  return slices;
}

export function removeGroup(envelopes, groupId) {
  const months = new Set();
  if (!groupId || !envelopes) return months;
  for (const e of envelopes) {
    e.transactions = getTransactions(e).filter((t) => {
      if (t.splitPurchaseGroupId === groupId) {
        if (t.month) months.add(t.month);
        return false;
      }
      return true;
    });
  }
  return months;
}

export function applyGroup(envelopes, existingGroupId, date, comment, receiptUri, slices, currentMonth) {
  const months = new Set();
  if (!envelopes || !slices || slices.length === 0) return months;
  const groupId = existingGroupId ? existingGroupId : randomUUID();
  for (const m of removeGroup(envelopes, groupId)) months.add(m);
  for (const slice of slices) {
    if (!slice) continue;
    const pond = slice.pondName;
    if (!pond || !String(pond).trim()) continue;
    const t = createTransaction(pond.trim(), slice.amount, date, comment);
    t.splitPurchaseGroupId = groupId;
    t.splitPurchaseBucketId = slice.bucketId;
    if (receiptUri) t.receiptImageUri = receiptUri;
    t.transferId = null;
    t.transferBucketId = null;
    const env = findEnvelopeByName(envelopes, pond.trim());
    if (env) {
      addTransaction(env, t, currentMonth);
      if (t.month) months.add(t.month);
    }
  }
  return months;
}

export function resolveForEdit(envelopes, clicked) {
  if (!isSplitPurchase(clicked)) return clicked;
  const peers = findTransactionsInGroup(envelopes, clicked.splitPurchaseGroupId);
  let best = null;
  let bestBid = null;
  for (const t of peers) {
    const bid = t.splitPurchaseBucketId || '';
    if (best == null || bid.localeCompare(bestBid) < 0) {
      best = t;
      bestBid = bid;
    }
  }
  return best != null ? best : clicked;
}

export function groupTotal(groupTransactions) {
  return draftAllocated(toAllocations(groupTransactions));
}

export function formatBreakdownLine(groupTransactions) {
  const sorted = [...groupTransactions].sort((a, b) => {
    const na = a.envelopeName || '';
    const nb = b.envelopeName || '';
    return na.localeCompare(nb, undefined, { sensitivity: 'accent' });
  });
  return sorted.map((t) => `${t.envelopeName || '?'}  $${Number(t.amount).toFixed(2)}`).join('\n');
}
