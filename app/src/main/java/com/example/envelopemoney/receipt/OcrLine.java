package com.example.envelopemoney.receipt;

import androidx.annotation.Nullable;

/**
 * One line of OCR output (text + optional confidence 0..1).
 * {@link #lineHeightPx} comes from ML Kit bounding boxes when available (0 in unit tests).
 */
public final class OcrLine {
    public final String text;
    public final float confidence;
    /** Pixel height of the line bbox; 0 when unknown. */
    public final int lineHeightPx;

    public OcrLine(String text, float confidence) {
        this(text, confidence, 0);
    }

    public OcrLine(String text, float confidence, int lineHeightPx) {
        this.text = text != null ? text : "";
        this.confidence = confidence;
        this.lineHeightPx = Math.max(0, lineHeightPx);
    }

    @Nullable
    public static OcrLine of(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return new OcrLine(text.trim(), 1f);
    }
}
