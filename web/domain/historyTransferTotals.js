import { roundToCents, formatSignedMoney } from './moneyMath.js';

/** Source transfer rows and ordinary spending count in Total. Destination mirrors do not. */
export function includeAmountInHistoryTotal(isTransfer, isSourceSide) {
  return !isTransfer || isSourceSide;
}

/**
 * spendGross is the sum of rows includeAmountInHistoryTotal kept.
 * When transfers are visible, allocated outgoing is also removed from Total.
 */
export function spendingTotal(spendGross, outgoingAllocated, subtractOutgoing) {
  let total = roundToCents(spendGross);
  if (subtractOutgoing) {
    total = roundToCents(total - outgoingAllocated);
  }
  return total;
}

/** Accumulate money transferred into a destination; always negative, never zeroed. */
export function addInboundToPanel(currentPanelTotal, allocationAmount) {
  return roundToCents((Number(currentPanelTotal) || 0) - Math.abs(Number(allocationAmount) || 0));
}

export function formatToSummary(envelopeName, inboundTotal) {
  const name = envelopeName == null ? '' : String(envelopeName);
  return `To ${name}: ${formatSignedMoney(inboundTotal)}`;
}
