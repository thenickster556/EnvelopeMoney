package com.example.envelopemoney;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure UI-side helper for grouped transfer controls.
 * Keeps slider snapping, scale-label generation, and validation gating testable
 * without depending on Android widgets.
 */
public final class TransferBucketUiHelper {
    public static final int DEFAULT_SCALE_LABEL_COUNT = 5;

    private TransferBucketUiHelper() {
    }

    public static double snapToStep(double amount, double stepAmount, double maxAmount) {
        double clampedAmount = clampNonNegative(amount, maxAmount);
        if (stepAmount <= 0d) {
            return roundCurrency(clampedAmount);
        }
        double snapped = Math.round(clampedAmount / stepAmount) * stepAmount;
        return roundCurrency(clampNonNegative(snapped, maxAmount));
    }

    public static double computeSliderMaximum(double maxAmountForBucket, double stepAmount) {
        if (maxAmountForBucket <= 0d) {
            return 0d;
        }
        if (stepAmount <= 0d) {
            return roundCurrency(maxAmountForBucket);
        }
        return Math.floor((maxAmountForBucket + 0.0001d) / stepAmount) * stepAmount;
    }

    public static boolean isAtSliderMaximum(float sliderValue, double sliderMaximum) {
        return sliderValue >= sliderMaximum - 0.0001f;
    }

    /**
     * When the slider is at its stepped maximum but true remainder includes odd cents, absorb them.
     */
    public static double resolveAmountAtSliderMax(float sliderValue,
                                                  double sliderMaximum,
                                                  double trueMaxAmount) {
        if (isAtSliderMaximum(sliderValue, sliderMaximum)) {
            return roundCurrency(Math.max(0d, trueMaxAmount));
        }
        return roundCurrency(sliderValue);
    }

    public static List<String> buildScaleLabels(double maxAmount) {
        return buildScaleLabels(maxAmount, DEFAULT_SCALE_LABEL_COUNT);
    }

    public static List<String> buildScaleLabels(double maxAmount, int labelCount) {
        int safeLabelCount = Math.max(2, labelCount);
        double safeMaxAmount = Math.max(0d, maxAmount);
        List<String> labels = new ArrayList<>(safeLabelCount);
        if (safeLabelCount == 2) {
            labels.add(formatCompactCurrency(0d));
            labels.add(formatCompactCurrency(safeMaxAmount));
            return labels;
        }

        for (int i = 0; i < safeLabelCount; i++) {
            double fraction = (double) i / (double) (safeLabelCount - 1);
            double snappedValue = snapToStep(safeMaxAmount * fraction, 0.50d, safeMaxAmount);
            labels.add(formatCompactCurrency(snappedValue));
        }
        return labels;
    }

    public static boolean shouldShowValidationMessage(boolean transferVisible,
                                                      boolean hasMeaningfulInteraction,
                                                      boolean saveAttempted,
                                                      TransferGroupValidationResult validationResult) {
        return transferVisible
                && validationResult != null
                && !validationResult.isValid()
                && (hasMeaningfulInteraction || saveAttempted);
    }

    public static int recommendedScaleLabelCount(int availableWidthPx, float density) {
        if (availableWidthPx <= 0 || density <= 0f) {
            return DEFAULT_SCALE_LABEL_COUNT;
        }
        float availableWidthDp = availableWidthPx / density;
        return availableWidthDp < 280f ? 3 : DEFAULT_SCALE_LABEL_COUNT;
    }

    private static double clampNonNegative(double amount, double maxAmount) {
        double safeMax = Math.max(0d, maxAmount);
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return 0d;
        }
        return Math.max(0d, Math.min(amount, safeMax));
    }

    private static double roundCurrency(double amount) {
        return Math.round(amount * 100d) / 100d;
    }

    private static String formatCompactCurrency(double amount) {
        double roundedAmount = roundCurrency(amount);
        if (Math.abs(roundedAmount - Math.rint(roundedAmount)) < 0.0001d) {
            return String.format(Locale.getDefault(), "$%.0f", roundedAmount);
        }
        return String.format(Locale.getDefault(), "$%.2f", roundedAmount);
    }
}
