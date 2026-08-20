import { prepareForLaunch } from './monthRollover.js';
import { realCurrentMonth, firstDayOfMonth, lastDayOfMonth, formatDisplayDate, parseYearMonth } from './dates.js';
import {
  calculateRemaining,
  initializeMonth,
  monthSpendForEnvelope,
  rebuildMonthData,
} from './envelopeModel.js';
import { compute, isReconciliationModeActive } from './pondBankReconciliation.js';
import { roundToCents } from './moneyMath.js';
import { ensureRecurringTransactions } from './recurring.js';

export function emptyProfile(now = new Date()) {
  const month = realCurrentMonth(now);
  const start = firstDayOfMonth(month);
  const end = lastDayOfMonth(month);
  return {
    currentMonth: month,
    displayedMonth: month,
    envelopes: [],
    envelopesCollapsed: false,
    lastAddTransactionEnvelope: null,
    lastAddTransferDestination: {},
    lastTransferTotalsOption: null,
    billsDays: [],
    paydays: [],
    billsFilterActive: false,
    billsFilterSavedStartDisplay: null,
    billsFilterSavedEndDisplay: null,
    dateFilterStartDisplay: start ? formatDisplayDate(start) : null,
    dateFilterEndDisplay: end ? formatDisplayDate(end) : null,
    transfersVisible: false,
  };
}

export function applyLaunchAndDisplay(profile, now = new Date()) {
  const source = profile && Array.isArray(profile.envelopes) ? profile.envelopes : [];
  const storedMonth = profile && profile.currentMonth;
  const result = prepareForLaunch(source, storedMonth, realCurrentMonth(now), true);
  const next = { ...emptyProfile(now), ...profile };
  next.envelopes = result.envelopes;
  next.currentMonth = result.activeMonth;
  if (!next.displayedMonth) next.displayedMonth = result.activeMonth;
  ensureRecurringTransactions(next.envelopes, next.displayedMonth || result.activeMonth);
  refreshBalances(next, now);
  return { profile: next, rollover: result };
}

export function refreshBalances(profile, now = new Date()) {
  const month = profile.displayedMonth || profile.currentMonth;
  const paydays = profile.paydays || [];
  const parsed = parseYearMonth(month);
  const visibleMonth = parsed ? parsed.date : now;
  for (const envelope of profile.envelopes || []) {
    initializeMonth(envelope, month, false);
    rebuildMonthData(envelope, month);
    const spend = roundToCents(monthSpendForEnvelope(envelope, month));
    if (isReconciliationModeActive(envelope.accountBalance, paydays)) {
      const r = compute(envelope.limit, envelope.accountBalance, paydays, visibleMonth, now, spend);
      envelope.manualRemaining = null;
      envelope.remaining = roundToCents(r.estimatedRemaining);
    } else {
      calculateRemaining(envelope, month);
    }
  }
}

export function pondReconciliation(envelope, profile, now = new Date()) {
  const paydays = profile.paydays || [];
  if (!isReconciliationModeActive(envelope.accountBalance, paydays)) return null;
  const month = profile.displayedMonth || profile.currentMonth;
  const parsed = parseYearMonth(month);
  const visibleMonth = parsed ? parsed.date : now;
  const spend = roundToCents(monthSpendForEnvelope(envelope, month));
  return compute(envelope.limit, envelope.accountBalance, paydays, visibleMonth, now, spend);
}

export function footerTotals(profile, now = new Date()) {
  const envelopes = profile.envelopes || [];
  const paydays = profile.paydays || [];
  const anyAccount = envelopes.some((e) => e.accountBalance != null);
  const reconActive = paydays.length > 0 && anyAccount;
  if (reconActive) {
    let inBank = 0;
    let still = 0;
    for (const e of envelopes) {
      const r = pondReconciliation(e, profile, now);
      if (r && r.active) {
        inBank += r.inBank;
        still += r.stillToDepositForMonth;
      }
    }
    return {
      mode: 'reconcile',
      inBank: roundToCents(inBank),
      stillToDeposit: roundToCents(still),
    };
  }
  let remaining = 0;
  let account = 0;
  for (const e of envelopes) {
    remaining += Number(e.remaining) || 0;
    if (e.accountBalance != null) account += Number(e.accountBalance) || 0;
  }
  if (anyAccount) {
    return {
      mode: 'full',
      account: roundToCents(account),
      remaining: roundToCents(remaining),
      difference: roundToCents(account - remaining),
    };
  }
  return { mode: 'partial', remaining: roundToCents(remaining) };
}

export function recalculateBalances(profile, now = new Date()) {
  refreshBalances(profile, now);
  return profile;
}

export function publicProfile(profile) {
  const { ...rest } = profile;
  return rest;
}
