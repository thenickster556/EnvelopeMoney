import {
  normalizeMonth,
  realCurrentMonth,
  shouldRollover,
} from './dates.js';
import {
  deepCopyEnvelopes,
  getMonthlyData,
  getMonthlyDataMap,
  getTransactions,
  getTransfers,
  rebuildMonthData,
  sanitizeState,
  replaceMonthData,
} from './envelopeModel.js';

function safeFinite(primary, fallback) {
  if (Number.isFinite(primary)) return primary;
  if (Number.isFinite(fallback)) return fallback;
  return 0;
}

function resolveCarryOverRemaining(envelope, fallbackValue) {
  const manualRemaining = envelope.manualRemaining;
  if (manualRemaining != null && Number.isFinite(manualRemaining)) return manualRemaining;
  if (Number.isFinite(envelope.remaining)) return envelope.remaining;
  return fallbackValue;
}

function sanitizeEnvelopeList(envelopes, fallbackMonth, warnings) {
  const cleaned = envelopes.filter((e) => e != null);
  envelopes.length = 0;
  envelopes.push(...cleaned);
  for (const envelope of envelopes) {
    try {
      sanitizeState(envelope, fallbackMonth);
    } catch {
      warnings.push(`Repaired envelope state for ${envelope.name}`);
      getTransactions(envelope).length = 0;
      getTransfers(envelope).length = 0;
      const map = getMonthlyDataMap(envelope);
      for (const key of Object.keys(map)) delete map[key];
      sanitizeState(envelope, fallbackMonth);
    }
  }
}

function applyRollover(envelope, sourceMonth, targetMonth, carryOver, warnings) {
  sanitizeState(envelope, sourceMonth);
  rebuildMonthData(envelope, sourceMonth);
  const originalLimit = safeFinite(envelope.originalLimit, envelope.limit);
  const sourceRemaining = resolveCarryOverRemaining(envelope, originalLimit);
  let targetLimit = carryOver ? originalLimit + sourceRemaining : originalLimit;
  targetLimit = safeFinite(targetLimit, originalLimit);
  envelope.originalLimit = originalLimit;
  envelope.limit = originalLimit;
  envelope.baselineLimit = targetLimit;
  envelope.baselineRemaining = targetLimit;
  envelope.manualRemaining = carryOver ? targetLimit : null;
  envelope.remaining = targetLimit;
  replaceMonthData(envelope, targetMonth, targetLimit);
  if (targetMonth !== sourceMonth) {
    rebuildMonthData(envelope, sourceMonth);
  }
  if (carryOver && targetLimit < 0) {
    warnings.push(`Negative carry-over was normalized for ${envelope.name}`);
  }
}

export function prepareForLaunch(sourceEnvelopes, storedMonth, actualMonth, carryOver = true) {
  let resolvedActualMonth = normalizeMonth(actualMonth);
  if (resolvedActualMonth == null) {
    resolvedActualMonth = realCurrentMonth();
  }
  const resolvedStoredMonth = normalizeMonth(storedMonth);
  const fallbackMonth = resolvedStoredMonth != null ? resolvedStoredMonth : resolvedActualMonth;
  const warnings = [];
  let workingCopy;
  try {
    workingCopy = deepCopyEnvelopes(sourceEnvelopes);
  } catch {
    workingCopy = [];
    warnings.push('Recovered from unreadable envelope state');
  }
  if (!Array.isArray(workingCopy)) {
    workingCopy = [];
    warnings.push('Recovered from unreadable envelope state');
  }

  sanitizeEnvelopeList(workingCopy, fallbackMonth, warnings);
  const rolloverNeeded = shouldRollover(resolvedStoredMonth, resolvedActualMonth);
  if (rolloverNeeded) {
    for (const envelope of workingCopy) {
      applyRollover(envelope, fallbackMonth, resolvedActualMonth, carryOver, warnings);
    }
  } else {
    for (const envelope of workingCopy) {
      sanitizeState(envelope, resolvedActualMonth);
      rebuildMonthData(envelope, resolvedActualMonth);
      if (envelope.manualRemaining == null) {
        envelope.remaining = getMonthlyData(envelope, resolvedActualMonth).remaining;
      }
    }
  }

  return {
    envelopes: workingCopy,
    activeMonth: resolvedActualMonth,
    requiresPersistence: rolloverNeeded || resolvedStoredMonth == null || warnings.length > 0,
    rolledOver: rolloverNeeded,
    warnings,
  };
}
