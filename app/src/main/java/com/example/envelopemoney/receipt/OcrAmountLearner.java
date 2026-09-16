package com.example.envelopemoney.receipt;

import com.example.envelopemoney.MoneyMath;

import java.util.List;

/**
 * Updates OCR fallback weights when the user saves a different amount than OCR guessed.
 *
 * <p>No change if the saved amount is not a money candidate on the lines, if OCR never ran,
 * or if the two amounts match at cents. Side-effect free besides the returned vector.
 */
public final class OcrAmountLearner {

    private OcrAmountLearner() {
    }

    public static float[] learn(List<String> lines,
                                Double ocrAmount,
                                double savedAmount,
                                float[] currentWeights,
                                ReceiptCaptureMode mode) {
        float[] current = OcrAmountWeights.copyOrDefault(currentWeights);
        if (ocrAmount == null || lines == null || lines.isEmpty()) {
            return current;
        }
        if (amountsMatch(ocrAmount, savedAmount)) {
            return current;
        }
        List<OcrMoneyCandidate> candidates = ReceiptFieldParser.listMoneyCandidates(lines, mode);
        OcrMoneyCandidate rejected = findByAmount(candidates, ocrAmount);
        OcrMoneyCandidate chosen = findByAmount(candidates, savedAmount);
        if (rejected == null || chosen == null) {
            return current;
        }
        boolean[] chosenFlags = chosen.featureFlags();
        boolean[] rejectedFlags = rejected.featureFlags();
        float[] next = OcrAmountWeights.copyOrDefault(current);
        for (int i = 0; i < OcrAmountWeights.FEATURE_COUNT; i++) {
            if (chosenFlags[i] && !rejectedFlags[i]) {
                next[i] += OcrAmountWeights.STEP;
            } else if (rejectedFlags[i] && !chosenFlags[i]) {
                next[i] -= OcrAmountWeights.STEP;
            }
        }
        return OcrAmountWeights.clamp(next);
    }

    private static boolean amountsMatch(double left, double right) {
        return Math.abs(MoneyMath.roundToCents(left) - MoneyMath.roundToCents(right)) < 0.001d;
    }

    private static OcrMoneyCandidate findByAmount(List<OcrMoneyCandidate> candidates, double amount) {
        for (OcrMoneyCandidate candidate : candidates) {
            if (amountsMatch(candidate.amount, amount)) {
                return candidate;
            }
        }
        return null;
    }
}
