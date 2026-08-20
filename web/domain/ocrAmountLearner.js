import { roundToCents } from './moneyMath.js';
import { copyOrDefault, clampWeights, FEATURE_COUNT, STEP } from './ocrAmountWeights.js';
import { listMoneyCandidates } from './receiptFieldParser.js';

/**
 * Updates OCR fallback weights when the saved amount differs from the OCR guess
 * and both amounts appear as money candidates on the lines.
 */
export function learn(lines, ocrAmount, savedAmount, currentWeights, mode) {
  const current = copyOrDefault(currentWeights);
  if (ocrAmount == null || !lines || lines.length === 0) return current;
  if (amountsMatch(ocrAmount, savedAmount)) return current;
  const candidates = listMoneyCandidates(lines, mode);
  const rejected = findByAmount(candidates, ocrAmount);
  const chosen = findByAmount(candidates, savedAmount);
  if (!rejected || !chosen) return current;
  const chosenFlags = featureFlags(chosen);
  const rejectedFlags = featureFlags(rejected);
  const next = copyOrDefault(current);
  for (let i = 0; i < FEATURE_COUNT; i++) {
    if (chosenFlags[i] && !rejectedFlags[i]) next[i] += STEP;
    else if (rejectedFlags[i] && !chosenFlags[i]) next[i] -= STEP;
  }
  return clampWeights(next);
}

function amountsMatch(left, right) {
  return Math.abs(roundToCents(left) - roundToCents(right)) < 0.001;
}

function findByAmount(candidates, amount) {
  return candidates.find((c) => amountsMatch(c.amount, amount)) || null;
}

function featureFlags(candidate) {
  return [
    candidate.dollarSign,
    candidate.strongTotalLabel,
    candidate.totalLabel,
    candidate.bottomHalf,
    candidate.orderOrPoints,
  ];
}
