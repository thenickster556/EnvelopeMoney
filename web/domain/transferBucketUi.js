export const DEFAULT_SCALE_LABEL_COUNT = 5;

export function snapToStep(amount, stepAmount, maxAmount) {
  const clampedAmount = clampNonNegative(amount, maxAmount);
  if (stepAmount <= 0) return roundCurrency(clampedAmount);
  const snapped = Math.round(clampedAmount / stepAmount) * stepAmount;
  return roundCurrency(clampNonNegative(snapped, maxAmount));
}

export function computeSliderMaximum(maxAmountForBucket, stepAmount) {
  if (maxAmountForBucket <= 0) return 0;
  if (stepAmount <= 0) return roundCurrency(maxAmountForBucket);
  return Math.floor((maxAmountForBucket + 0.0001) / stepAmount) * stepAmount;
}

export function isAtSliderMaximum(sliderValue, sliderMaximum) {
  return sliderValue >= sliderMaximum - 0.0001;
}

export function resolveAmountAtSliderMax(sliderValue, sliderMaximum, trueMaxAmount) {
  if (isAtSliderMaximum(sliderValue, sliderMaximum)) {
    return roundCurrency(Math.max(0, trueMaxAmount));
  }
  return roundCurrency(sliderValue);
}

export function buildScaleLabels(maxAmount, labelCount = DEFAULT_SCALE_LABEL_COUNT) {
  const safeLabelCount = Math.max(2, labelCount);
  const safeMaxAmount = Math.max(0, maxAmount);
  const labels = [];
  if (safeLabelCount === 2) {
    labels.push(formatCompactCurrency(0));
    labels.push(formatCompactCurrency(safeMaxAmount));
    return labels;
  }
  for (let i = 0; i < safeLabelCount; i++) {
    const fraction = i / (safeLabelCount - 1);
    const snappedValue = snapToStep(safeMaxAmount * fraction, 0.5, safeMaxAmount);
    labels.push(formatCompactCurrency(snappedValue));
  }
  return labels;
}

export function shouldShowValidationMessage(transferVisible, hasMeaningfulInteraction, saveAttempted, validationResult) {
  return transferVisible
    && validationResult != null
    && !validationResult.valid
    && (hasMeaningfulInteraction || saveAttempted);
}

export function recommendedScaleLabelCount(availableWidthPx, density) {
  if (availableWidthPx <= 0 || density <= 0) return DEFAULT_SCALE_LABEL_COUNT;
  const availableWidthDp = availableWidthPx / density;
  return availableWidthDp < 280 ? 3 : DEFAULT_SCALE_LABEL_COUNT;
}

function clampNonNegative(amount, maxAmount) {
  const safeMax = Math.max(0, maxAmount);
  if (!Number.isFinite(amount)) return 0;
  return Math.max(0, Math.min(amount, safeMax));
}

function roundCurrency(amount) {
  return Math.round(amount * 100) / 100;
}

function formatCompactCurrency(amount) {
  const roundedAmount = roundCurrency(amount);
  if (Math.abs(roundedAmount - Math.round(roundedAmount)) < 0.0001) {
    return `$${roundedAmount.toFixed(0)}`;
  }
  return `$${roundedAmount.toFixed(2)}`;
}
