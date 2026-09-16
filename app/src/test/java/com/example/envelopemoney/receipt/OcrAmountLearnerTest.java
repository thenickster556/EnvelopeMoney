package com.example.envelopemoney.receipt;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OcrAmountLearnerTest {

    private static final List<String> TWO_TOTAL_LINES = Arrays.asList(
            "Store",
            "$45.12",
            "misc",
            "42.00"
    );

    @Test
    public void learn_bumpsDifferingFeaturesAndClamps() {
        float[] start = OcrAmountWeights.defaults();
        float[] next = OcrAmountLearner.learn(
                TWO_TOTAL_LINES, 45.12, 42.00, start, ReceiptCaptureMode.RECEIPT);
        assertTrue(next[OcrAmountWeights.DOLLAR_SIGN] < start[OcrAmountWeights.DOLLAR_SIGN]);
        assertEquals(0f, OcrAmountLearner.learn(
                TWO_TOTAL_LINES, 45.12, 42.00,
                new float[]{5f, 80f, 60f, 25f, -100f},
                ReceiptCaptureMode.RECEIPT)[OcrAmountWeights.DOLLAR_SIGN], 0.001f);
    }

    @Test
    public void learn_skipsWhenSavedAmountIsNotACandidate() {
        float[] start = OcrAmountWeights.defaults();
        float[] next = OcrAmountLearner.learn(
                TWO_TOTAL_LINES, 45.12, 99.99, start, ReceiptCaptureMode.RECEIPT);
        assertArrayEquals(start, next, 0.001f);
    }

    @Test
    public void learn_skipsWhenAmountsMatch() {
        float[] start = OcrAmountWeights.defaults();
        float[] next = OcrAmountLearner.learn(
                TWO_TOTAL_LINES, 45.12, 45.12, start, ReceiptCaptureMode.RECEIPT);
        assertArrayEquals(start, next, 0.001f);
    }
}
