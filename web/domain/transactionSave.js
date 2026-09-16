import { createTransaction, addTransaction, getTransactions } from './envelopeModel.js';
import { findByName } from './pondLookup.js';
import { isSplitPurchase } from './splitSync.js';
import { validate as validateTransfer } from './transferGroup.js';
import { applyTransferGroup, detachTransferGroup } from './transferSync.js';

/**
 * Removes a transaction object from every pond list. Remaining is refreshed by the caller.
 */
export function removePlainTransaction(envelopes, tx) {
  if (!envelopes || !tx) return;
  for (const envelope of envelopes) {
    envelope.transactions = getTransactions(envelope).filter((candidate) => candidate !== tx);
  }
}

function applyFields(tx, input) {
  tx.amount = input.amount;
  tx.date = input.date;
  tx.month = input.date ? String(input.date).slice(0, 7) : tx.month;
  tx.comment = input.comment;
  tx.envelopeName = input.pondName;
  tx.receiptImageUri = input.receiptUri;
}

/**
 * Places or updates a spending/transfer row. Transfers are validated before any insert.
 * @returns {{ ok: true, tx: object } | { ok: false, message: string }}
 */
export function saveSpendingOrTransfer(envelopes, input) {
  if (input.type === 'transfer') {
    const validation = validateTransfer(input.amount, input.pondName, input.buckets);
    if (!validation.valid) {
      return { ok: false, message: validation.message };
    }
  }

  let tx = input.source;
  const pondChanged = !!(tx && tx.envelopeName !== input.pondName);
  const createNew = !tx || isSplitPurchase(tx) || (pondChanged && !tx.transferId);

  if (createNew) {
    tx = createTransaction(input.pondName, input.amount, input.date, input.comment);
    tx.receiptImageUri = input.receiptUri;
    const destination = findByName(envelopes, input.pondName);
    addTransaction(destination, tx, input.month);
    if (input.isEdit && input.existing && input.existing !== tx) {
      removePlainTransaction(envelopes, input.existing);
    }
  } else if (pondChanged && tx.transferId) {
    removePlainTransaction(envelopes, tx);
    applyFields(tx, input);
    const destination = findByName(envelopes, input.pondName);
    addTransaction(destination, tx, input.month);
  } else {
    applyFields(tx, input);
  }

  if (input.type === 'transfer') {
    applyTransferGroup(envelopes, tx, input.pondName, input.buckets);
  } else if (tx.transferId) {
    detachTransferGroup(envelopes, tx);
  }

  return { ok: true, tx };
}
