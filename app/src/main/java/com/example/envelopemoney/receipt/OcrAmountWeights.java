package com.example.envelopemoney.receipt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Fixed-length OCR fallback weight vector stored as a little-endian float32 blob.
 *
 * <p>Index order: dollarSign, strongTotalLabel, totalLabel, bottomHalf, orderOrPointsPenalty.
 * Defaults match {@link ReceiptFieldParser} fallback scoring constants.
 */
public final class OcrAmountWeights {

    public static final int FEATURE_COUNT = 5;
    public static final int DOLLAR_SIGN = 0;
    public static final int STRONG_TOTAL_LABEL = 1;
    public static final int TOTAL_LABEL = 2;
    public static final int BOTTOM_HALF = 3;
    public static final int ORDER_OR_POINTS_PENALTY = 4;

    public static final float STEP = 25f;
    public static final float CLAMP_MIN = 0f;
    public static final float CLAMP_MAX = 120f;
    public static final float PENALTY_MIN = -150f;
    public static final float PENALTY_MAX = 0f;

    private static final float[] DEFAULTS = new float[]{50f, 80f, 60f, 25f, -100f};

    private OcrAmountWeights() {
    }

    public static float[] defaults() {
        return Arrays.copyOf(DEFAULTS, FEATURE_COUNT);
    }

    public static float[] copyOrDefault(float[] weights) {
        if (weights == null || weights.length != FEATURE_COUNT) {
            return defaults();
        }
        return Arrays.copyOf(weights, FEATURE_COUNT);
    }

    public static byte[] toLittleEndianBlob(float[] weights) {
        float[] w = copyOrDefault(weights);
        ByteBuffer buffer = ByteBuffer.allocate(FEATURE_COUNT * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < FEATURE_COUNT; i++) {
            buffer.putFloat(w[i]);
        }
        return buffer.array();
    }

    /**
     * Decodes a 20-byte little-endian blob. Null, short, or unreadable data returns defaults.
     */
    public static float[] fromLittleEndianBlob(byte[] blob) {
        if (blob == null || blob.length != FEATURE_COUNT * 4) {
            return defaults();
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
            float[] out = new float[FEATURE_COUNT];
            for (int i = 0; i < FEATURE_COUNT; i++) {
                out[i] = buffer.getFloat();
            }
            return out;
        } catch (Exception ignored) {
            return defaults();
        }
    }

    public static float[] clamp(float[] weights) {
        float[] w = copyOrDefault(weights);
        for (int i = 0; i < ORDER_OR_POINTS_PENALTY; i++) {
            w[i] = Math.min(CLAMP_MAX, Math.max(CLAMP_MIN, w[i]));
        }
        w[ORDER_OR_POINTS_PENALTY] = Math.min(PENALTY_MAX, Math.max(PENALTY_MIN, w[ORDER_OR_POINTS_PENALTY]));
        return w;
    }
}
