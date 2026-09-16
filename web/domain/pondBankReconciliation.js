import { roundToCents, splitTotalByPercents } from './moneyMath.js';
import { daysInMonth, startOfDay } from './dates.js';

export function isReconciliationModeActive(accountBalance, paydays) {
  return accountBalance != null && paydays != null && paydays.length > 0;
}

export function compute(monthTargetRaw, accountBalanceRaw, paydayDaysOfMonth, visibleMonth, today, monthSpendRaw = 0) {
  const monthTarget = roundToCents(monthTargetRaw);
  if (accountBalanceRaw == null) {
    return inactive(monthTarget);
  }

  const inBank = roundToCents(accountBalanceRaw);
  const monthSpend = roundToCents(monthSpendRaw);
  const month = startOfDay(visibleMonth);
  const todayMidnight = startOfDay(today);
  const paydaysInMonth = resolvePaydayDaysInMonth(paydayDaysOfMonth, month);
  const paydaysPassed = countPaydaysOnOrBefore(paydaysInMonth, todayMidnight, month);
  const paydayCount = paydaysInMonth.length;
  const unlockedFromPaydays = unlockedAmount(monthTarget, paydayCount, paydaysPassed);
  const stillToDepositForMonth = stillToDepositAmount(monthTarget, paydayCount, paydaysPassed);
  const perPayday = paydayCount === 0 ? 0 : roundToCents(monthTarget / paydayCount);
  const aheadOfTarget = roundToCents(Math.max(0, inBank - monthTarget));
  const estimatedRemaining = roundToCents(inBank + unlockedFromPaydays - monthSpend);

  return {
    active: true,
    monthTarget,
    inBank,
    stillToDepositForMonth,
    fullMonthStillToDeposit: monthTarget,
    perPayday,
    paydaysInMonth: paydayCount,
    paydaysPassed,
    expectedInBankByToday: unlockedFromPaydays,
    behindSchedule: stillToDepositForMonth,
    aheadOfTarget,
    unlockedFromPaydays,
    estimatedRemaining,
  };
}

export function unlockedAmount(monthTarget, paydayCount, paydaysPassed) {
  if (paydayCount <= 0 || paydaysPassed <= 0) return 0;
  const passed = Math.min(paydaysPassed, paydayCount);
  const shares = fairPaydayShares(monthTarget, paydayCount);
  let unlocked = 0;
  for (let i = 0; i < passed; i++) unlocked += shares[i];
  return roundToCents(unlocked);
}

export function stillToDepositAmount(monthTarget, paydayCount, paydaysPassed) {
  if (paydayCount <= 0) return 0;
  const passed = Math.min(Math.max(paydaysPassed, 0), paydayCount);
  const shares = fairPaydayShares(monthTarget, paydayCount);
  let still = 0;
  for (let i = passed; i < paydayCount; i++) still += shares[i];
  return roundToCents(still);
}

export function fairPaydayShares(monthTarget, paydayCount) {
  if (paydayCount <= 0) return [];
  const equalPercents = new Array(paydayCount);
  const base = Math.trunc(100 / paydayCount);
  const remainder = 100 - base * paydayCount;
  for (let i = 0; i < paydayCount; i++) equalPercents[i] = base;
  for (let i = 0; i < remainder; i++) equalPercents[i] += 1;
  return splitTotalByPercents(monthTarget, equalPercents);
}

export function resolvePaydayDaysInMonth(paydayDaysOfMonth, month) {
  if (!paydayDaysOfMonth || paydayDaysOfMonth.length === 0) return [];
  const maxInMonth = daysInMonth(month.getFullYear(), month.getMonth());
  const unique = new Set();
  for (const day of paydayDaysOfMonth) {
    if (day != null && day >= 1 && day <= 31) {
      unique.add(Math.min(day, maxInMonth));
    }
  }
  return Array.from(unique).sort((a, b) => a - b);
}

export function countPaydaysOnOrBefore(paydaysInMonth, today, month) {
  if (!paydaysInMonth.length) return 0;
  const monthYear = month.getFullYear();
  const monthMonth = month.getMonth();
  const todayYear = today.getFullYear();
  const todayMonth = today.getMonth();
  if (monthYear < todayYear || (monthYear === todayYear && monthMonth < todayMonth)) {
    return paydaysInMonth.length;
  }
  if (monthYear > todayYear || (monthYear === todayYear && monthMonth > todayMonth)) {
    return 0;
  }
  const todayDayOfMonth = today.getDate();
  let count = 0;
  for (const day of paydaysInMonth) {
    if (day <= todayDayOfMonth) count++;
  }
  return count;
}

function inactive(monthTarget) {
  return {
    active: false,
    monthTarget: roundToCents(monthTarget),
    inBank: 0,
    stillToDepositForMonth: 0,
    fullMonthStillToDeposit: 0,
    perPayday: 0,
    paydaysInMonth: 0,
    paydaysPassed: 0,
    expectedInBankByToday: 0,
    behindSchedule: 0,
    aheadOfTarget: 0,
    unlockedFromPaydays: 0,
    estimatedRemaining: 0,
  };
}
