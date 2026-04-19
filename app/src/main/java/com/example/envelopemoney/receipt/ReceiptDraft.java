package com.example.envelopemoney.receipt;

import androidx.annotation.Nullable;

/**
 * Proposed transaction fields from OCR + heuristics (user confirms in dialog).
 */
public final class ReceiptDraft {
    @Nullable
    public final String merchantForComment;
    @Nullable
    public final Double totalAmount;
    @Nullable
    public final String dateYyyyMmDd;
    /** 0..1 aggregate confidence for amount extraction. */
    public final float amountConfidence;
    @Nullable
    public final String rawOcrSample;

    public ReceiptDraft(@Nullable String merchantForComment,
                        @Nullable Double totalAmount,
                        @Nullable String dateYyyyMmDd,
                        float amountConfidence,
                        @Nullable String rawOcrSample) {
        this.merchantForComment = merchantForComment;
        this.totalAmount = totalAmount;
        this.dateYyyyMmDd = dateYyyyMmDd;
        this.amountConfidence = amountConfidence;
        this.rawOcrSample = rawOcrSample;
    }
}
