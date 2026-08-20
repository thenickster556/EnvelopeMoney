/** Cent-precision helpers matching Android MoneyMath (BigDecimal HALF_UP scale 2). */

const PERCENT_TOTAL = 100;

export function roundToCents(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return 0;
  return Number(Math.round(Number(`${n}e+2`)) + 'e-2');
}

/**
 * Integer percent weights that sum to 100. First share uses ceiling(100/n).
 * n=3 → 34, 33, 33.
 */
export function splitIntegerPercentsFirstCeiling(parts) {
  if (parts <= 0) return [];
  if (parts === 1) return [PERCENT_TOTAL];
  const percents = new Array(parts);
  percents[0] = Math.floor((PERCENT_TOTAL + parts - 1) / parts);
  const eachOther = Math.floor((PERCENT_TOTAL - percents[0]) / (parts - 1));
  for (let i = 1; i < parts; i++) {
    percents[i] = eachOther;
  }
  return percents;
}

/**
 * Dollar amounts from total and percent weights. Last bucket absorbs remainder cents.
 */
export function splitTotalByPercents(total, percents) {
  if (!percents || percents.length === 0) return [];
  const safeTotal = roundToCents(Math.max(0, total));
  const totalCents = Math.round(safeTotal * 100);
  const amounts = new Array(percents.length);
  let allocatedCents = 0;
  for (let i = 0; i < percents.length; i++) {
    if (i === percents.length - 1) {
      amounts[i] = roundToCents((totalCents - allocatedCents) / 100);
    } else {
      const bucketCents = Math.trunc((totalCents * percents[i]) / PERCENT_TOTAL);
      amounts[i] = roundToCents(bucketCents / 100);
      allocatedCents += bucketCents;
    }
  }
  return amounts;
}
