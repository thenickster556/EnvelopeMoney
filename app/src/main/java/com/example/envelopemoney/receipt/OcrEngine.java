package com.example.envelopemoney.receipt;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Pluggable OCR: default app wiring uses on-device Latin text recognition suitable for phones.
 * Swap implementation for PaddleOCR JNI when native models are integrated (plan: PaddleOCRAdapter).
 */
public interface OcrEngine {

    /**
     * Run OCR on a bitmap (caller may downscale before invoke).
     */
    void recognizeAsync(Context context, Bitmap bitmap, OcrCallback callback);

    interface OcrCallback {
        void onSuccess(OcrResult result);

        void onFailure(Exception error);
    }
}
