/**
 * Point restored transactions at pictures already in the connected folder.
 * A missing name or two files with the same name leaves the stored pointer unchanged.
 */
import { parseFileReference, serializeFileReference } from './fileReference.js';

function fileBaseName(path) {
  const value = String(path || '');
  const slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
  return slash >= 0 ? value.slice(slash + 1) : value;
}

function eachTransaction(envelopes, visit) {
  for (const envelope of envelopes || []) {
    if (!envelope || typeof envelope !== 'object') continue;
    for (const tx of envelope.transactions || []) visit(tx);
    const monthly = envelope.monthlyData;
    if (!monthly || typeof monthly !== 'object') continue;
    for (const month of Object.values(monthly)) {
      if (!month || typeof month !== 'object') continue;
      for (const tx of month.transactions || []) visit(tx);
    }
  }
}

export function relinkLocalReceipts(envelopes, files) {
  const byName = new Map();
  for (const file of files || []) {
    const name = file && (file.name || fileBaseName(file.path));
    if (!name) continue;
    const list = byName.get(name) || [];
    list.push(String(file.path || '').replace(/^\/+/, ''));
    byName.set(name, list);
  }
  const next = JSON.parse(JSON.stringify(envelopes || []));
  let changed = 0;
  eachTransaction(next, (tx) => {
    if (!tx || typeof tx !== 'object') return;
    const name = typeof tx.receiptImageFileName === 'string' ? tx.receiptImageFileName : '';
    if (!name) return;
    const matches = byName.get(name) || [];
    if (matches.length !== 1) return;
    const uri = serializeFileReference({ storage: 'local', path: matches[0] });
    if (tx.receiptImageUri !== uri) {
      tx.receiptImageUri = uri;
      changed += 1;
    }
  });
  return { envelopes: next, changed };
}

/**
 * Red photo icon when this browser cannot open the stored picture.
 * Server mode can still open GridFS. A content URI or a server id in device mode cannot.
 */
export function receiptNeedsAttention({ uri, mode, localExists }) {
  if (!uri) return false;
  const ref = parseFileReference(uri);
  if (ref.storage === 'local') return !localExists;
  if (ref.storage === 'gridfs' && mode === 'server') return false;
  return true;
}
