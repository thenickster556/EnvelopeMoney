package com.example.envelopemoney.receipt;

import android.content.Context;

/**
 * Factory for the active {@link OcrEngine}. Plan target: PaddleOCR native; current default is
 * on-device Latin ML Kit (offline-capable after model init) behind the same interface.
 */
public final class PaddleOcrAdapter {

    private PaddleOcrAdapter() {
    }

    /**
     * Production default until Paddle JNI + models are bundled.
     */
    public static OcrEngine createDefaultEngine() {
        return new MlKitLatinOcrEngine();
    }
}
