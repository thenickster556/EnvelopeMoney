package com.example.envelopemoney.receipt;

import androidx.annotation.Nullable;

/**
 * One line of OCR output (text + optional confidence 0..1).
 */
public final class OcrLine {
    public final String text;
    public final float confidence;

    public OcrLine(String text, float confidence) {
        this.text = text != null ? text : "";
        this.confidence = confidence;
    }

    @Nullable
    public static OcrLine of(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return new OcrLine(text.trim(), 1f);
    }
}
