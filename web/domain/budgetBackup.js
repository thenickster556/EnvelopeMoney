/**
 * Ledger-only budget file shared with Android BudgetBackup.
 * Photos, passwords, and hashes are rejected. A bad file must not be applied.
 */
import { firstDayOfMonth, formatDisplayDate, lastDayOfMonth } from './dates.js';

export const BUDGET_KIND = 'mountain-money-budget';
export const BUDGET_VERSION = 1;
export const MAX_BACKUP_CHARS = 2000000;

const FORBIDDEN_KEYS = new Set(['password', 'passwordhash', 'photobase64', 'imagebase64', 'jpegbase64']);

function fail(error) {
  return {
    ok: false,
    error,
    currentMonth: null,
    envelopes: [],
    billsDays: [],
    paydays: [],
    billsFilterActive: false,
    billsFilterSavedStartDisplay: null,
    billsFilterSavedEndDisplay: null,
    learningPresent: false,
    comments: [],
    ocrWeights: null,
  };
}

function validFileName(name) {
  return typeof name === 'string' && name.includes('.') && !name.startsWith('.')
    && !name.includes('/') && !name.includes('\\') && !name.includes('\u0000');
}

/** Same hint Android uses: URI fragment, then a path name. Bare MediaStore ids are not names. */
export function fileNameFromReference(reference) {
  if (reference == null || reference === '') return null;
  const value = String(reference);
  const hash = value.indexOf('#');
  if (hash >= 0) {
    let fragment = value.slice(hash + 1);
    try {
      fragment = decodeURIComponent(fragment);
    } catch {
      /* keep the raw fragment */
    }
    if (validFileName(fragment)) return fragment;
  }
  let path = hash >= 0 ? value.slice(0, hash) : value;
  const query = path.indexOf('?');
  if (query >= 0) path = path.slice(0, query);
  const slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
  const name = slash >= 0 ? path.slice(slash + 1) : path;
  return validFileName(name) ? name : null;
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

function fillReceiptFileNames(envelopes) {
  eachTransaction(envelopes, (tx) => {
    if (!tx || typeof tx !== 'object') return;
    if (typeof tx.receiptImageFileName === 'string' && tx.receiptImageFileName.trim()) return;
    const name = fileNameFromReference(tx.receiptImageUri);
    if (name) tx.receiptImageFileName = name;
  });
}

function containsForbidden(value) {
  if (typeof value === 'string') return value.toLowerCase().startsWith('data:image');
  if (Array.isArray(value)) return value.some(containsForbidden);
  if (value && typeof value === 'object') {
    for (const [key, child] of Object.entries(value)) {
      if (FORBIDDEN_KEYS.has(String(key).toLowerCase())) return true;
      if (containsForbidden(child)) return true;
    }
  }
  return false;
}

function intList(value) {
  if (!Array.isArray(value)) return [];
  return value.filter((item) => Number.isInteger(item));
}

function commentList(value) {
  if (!Array.isArray(value)) return [];
  const comments = [];
  for (const item of value) {
    if (typeof item !== 'string') continue;
    const trimmed = item.trim();
    if (!trimmed) continue;
    comments.push(trimmed);
    if (comments.length >= 50) break;
  }
  return comments;
}

function weightList(value) {
  if (!Array.isArray(value) || value.length !== 5) return null;
  if (!value.every((item) => typeof item === 'number' && Number.isFinite(item))) return null;
  return value.slice();
}

export function parseBudgetBackup(text) {
  if (typeof text !== 'string') return fail('not-json');
  if (text.length > MAX_BACKUP_CHARS) return fail('too-large');
  let root;
  try {
    root = JSON.parse(text);
  } catch {
    return fail('not-json');
  }
  if (!root || typeof root !== 'object' || Array.isArray(root)) return fail('not-json');
  if (containsForbidden(root)) return fail('forbidden');
  if (root.kind !== BUDGET_KIND) return fail('wrong-kind');
  if (typeof root.version !== 'number' || !Number.isFinite(root.version)) return fail('bad-version');
  if (root.version > BUDGET_VERSION) return fail('newer-version');
  if (root.version !== BUDGET_VERSION) return fail('bad-version');
  if (!Array.isArray(root.envelopes)) return fail('missing-envelopes');

  const envelopes = JSON.parse(JSON.stringify(root.envelopes));
  fillReceiptFileNames(envelopes);
  let learningPresent = false;
  let comments = [];
  let ocrWeights = null;
  if (root.learning && typeof root.learning === 'object' && !Array.isArray(root.learning)) {
    learningPresent = true;
    comments = commentList(root.learning.comments);
    ocrWeights = weightList(root.learning.ocrWeights);
  }
  return {
    ok: true,
    error: null,
    currentMonth: typeof root.currentMonth === 'string' ? root.currentMonth : null,
    envelopes,
    billsDays: intList(root.billsDays),
    paydays: intList(root.paydays),
    billsFilterActive: !!root.billsFilterActive,
    billsFilterSavedStartDisplay: typeof root.billsFilterSavedStartDisplay === 'string'
      ? root.billsFilterSavedStartDisplay : null,
    billsFilterSavedEndDisplay: typeof root.billsFilterSavedEndDisplay === 'string'
      ? root.billsFilterSavedEndDisplay : null,
    learningPresent,
    comments,
    ocrWeights,
  };
}

export function buildBudgetBackup(snapshot) {
  const envelopes = JSON.parse(JSON.stringify(snapshot.envelopes || []));
  fillReceiptFileNames(envelopes);
  const document = {
    kind: BUDGET_KIND,
    version: BUDGET_VERSION,
    currentMonth: snapshot.currentMonth || null,
    billsDays: snapshot.billsDays || [],
    paydays: snapshot.paydays || [],
    billsFilterActive: !!snapshot.billsFilterActive,
    billsFilterSavedStartDisplay: snapshot.billsFilterSavedStartDisplay ?? null,
    billsFilterSavedEndDisplay: snapshot.billsFilterSavedEndDisplay ?? null,
    envelopes,
  };
  if (snapshot.learningPresent) {
    document.learning = {
      comments: snapshot.comments || [],
      ocrWeights: snapshot.ocrWeights || null,
    };
  }
  return JSON.stringify(document);
}

function summaryOf(parsed) {
  const envelope = parsed.ok && parsed.envelopes && parsed.envelopes[0] ? parsed.envelopes[0] : null;
  const tx = envelope && Array.isArray(envelope.transactions) && envelope.transactions[0]
    ? envelope.transactions[0] : null;
  return {
    ok: !!parsed.ok,
    error: parsed.error,
    currentMonth: parsed.ok ? parsed.currentMonth : null,
    envelopeName: envelope && envelope.name != null ? envelope.name : null,
    amount: tx && typeof tx.amount === 'number' ? tx.amount : null,
    transferId: tx && tx.transferId != null ? tx.transferId : null,
    splitPurchaseGroupId: tx && tx.splitPurchaseGroupId != null ? tx.splitPurchaseGroupId : null,
    receiptImageFileName: tx && tx.receiptImageFileName ? tx.receiptImageFileName : null,
    learningPresent: !!(parsed.ok && parsed.learningPresent),
    comment: parsed.ok && parsed.comments && parsed.comments[0] ? parsed.comments[0] : null,
    weight0: parsed.ok && parsed.ocrWeights ? parsed.ocrWeights[0] : null,
    billsDay0: parsed.ok && parsed.billsDays && parsed.billsDays.length ? parsed.billsDays[0] : null,
  };
}

/** Parse, write, and parse again so a filename filled from a URI fragment survives. */
export function summarizeBudget(document) {
  const first = parseBudgetBackup(JSON.stringify(document));
  const parsed = first.ok ? parseBudgetBackup(buildBudgetBackup(first)) : first;
  return summaryOf(parsed);
}

export function applyBudgetToProfile(profile, parsed) {
  const month = parsed.currentMonth || (profile && profile.currentMonth) || null;
  const start = firstDayOfMonth(month);
  const end = lastDayOfMonth(month);
  return {
    ...(profile || {}),
    currentMonth: month,
    displayedMonth: month,
    envelopes: parsed.envelopes || [],
    billsDays: parsed.billsDays || [],
    paydays: parsed.paydays || [],
    billsFilterActive: !!parsed.billsFilterActive,
    billsFilterSavedStartDisplay: parsed.billsFilterSavedStartDisplay,
    billsFilterSavedEndDisplay: parsed.billsFilterSavedEndDisplay,
    dateFilterStartDisplay: start ? formatDisplayDate(start) : profile && profile.dateFilterStartDisplay,
    dateFilterEndDisplay: end ? formatDisplayDate(end) : profile && profile.dateFilterEndDisplay,
  };
}
