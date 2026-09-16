import { createTransaction, getTransfers, getTransactions } from './envelopeModel.js';
import { allocatedTotal as draftAllocated } from './transferGroup.js';
import { randomUUID } from './id.js';

function findEnvelopeByName(envelopes, envelopeName) {
  if (!envelopes || envelopeName == null) return null;
  for (const envelope of envelopes) {
    if (envelope && envelope.name === envelopeName) return envelope;
  }
  return null;
}

function normalizeBucketId(transfer) {
  const bucketId = transfer.bucketId;
  if (bucketId == null || bucketId === '') {
    const dest = transfer.toEnvelope == null ? 'unknown' : transfer.toEnvelope;
    return `legacy-${transfer.id}-${dest}`;
  }
  return bucketId;
}

export function findTransferOwner(envelopes, transferId) {
  if (!transferId || !envelopes) return null;
  for (const envelope of envelopes) {
    for (const transfer of getTransfers(envelope)) {
      if (transfer.id === transferId) return envelope;
    }
  }
  return null;
}

export function resolveAnchorTransaction(envelopes, transaction) {
  if (!transaction) return null;
  const transferId = transaction.transferId;
  if (!transferId) return transaction;
  const owner = findTransferOwner(envelopes, transferId);
  if (!owner) return transaction;
  for (const candidate of getTransactions(owner)) {
    if (candidate.transferId === transferId && (!candidate.transferBucketId)) {
      return candidate;
    }
  }
  return transaction;
}

export function getAllocations(envelopes, transferId) {
  const allocations = [];
  if (!transferId) return allocations;
  const owner = findTransferOwner(envelopes, transferId);
  if (!owner) return allocations;
  let legacyIndex = 0;
  for (const transfer of getTransfers(owner)) {
    if (transfer.id !== transferId) continue;
    let bucketId = transfer.bucketId;
    if (!bucketId) {
      bucketId = `legacy-${transferId}-${legacyIndex++}`;
    }
    allocations.push({ bucketId, toEnvelope: transfer.toEnvelope, amount: transfer.amount });
  }
  return allocations;
}

export function allocatedTotal(envelopes, transferId) {
  return draftAllocated(getAllocations(envelopes, transferId));
}

function removeTransferGroupData(envelopes, transferId) {
  for (const envelope of envelopes) {
    envelope.transfers = getTransfers(envelope).filter((t) => t.id !== transferId);
  }
}

function removeMirrorTransactions(envelopes, transferId, sourceTransaction) {
  for (const envelope of envelopes) {
    envelope.transactions = getTransactions(envelope).filter((candidate) => {
      if (candidate.transferId !== transferId) return true;
      if (candidate === sourceTransaction) return true;
      return false;
    });
  }
}

export function detachTransferGroup(envelopes, sourceTransaction) {
  if (!sourceTransaction) return;
  const transferId = sourceTransaction.transferId;
  if (!transferId) {
    sourceTransaction.transferBucketId = null;
    return;
  }
  removeTransferGroupData(envelopes, transferId);
  removeMirrorTransactions(envelopes, transferId, sourceTransaction);
  sourceTransaction.transferId = null;
  sourceTransaction.transferBucketId = null;
}

export function applyTransferGroup(envelopes, sourceTransaction, sourceEnvelopeName, allocations) {
  if (!sourceTransaction) return;
  let transferId = sourceTransaction.transferId;
  if (!transferId) {
    transferId = randomUUID();
    sourceTransaction.transferId = transferId;
  }
  sourceTransaction.transferBucketId = null;
  removeTransferGroupData(envelopes, transferId);
  removeMirrorTransactions(envelopes, transferId, sourceTransaction);
  const owner = findEnvelopeByName(envelopes, sourceEnvelopeName);
  if (!owner) return;
  for (const allocation of allocations) {
    if (!allocation || !allocation.toEnvelope || !String(allocation.toEnvelope).trim()) continue;
    getTransfers(owner).push({
      id: transferId,
      bucketId: allocation.bucketId,
      toEnvelope: allocation.toEnvelope,
      amount: Math.abs(allocation.amount),
    });
    const destination = findEnvelopeByName(envelopes, allocation.toEnvelope);
    if (!destination) continue;
    const sourceComment = sourceTransaction.comment;
    const mirrorComment = !sourceComment
      ? `Transfer from ${sourceEnvelopeName}`
      : `Transfer from ${sourceEnvelopeName} | ${sourceComment}`;
    const mirror = createTransaction(
      allocation.toEnvelope,
      -Math.abs(allocation.amount),
      sourceTransaction.date,
      mirrorComment,
    );
    mirror.transferId = transferId;
    mirror.transferBucketId = allocation.bucketId;
    mirror.receiptImageUri = sourceTransaction.receiptImageUri;
    getTransactions(destination).push(mirror);
  }
}
