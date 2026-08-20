package com.example.envelopemoney.receipt;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

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
    /** Trimmed OCR lines used for parse (for amount-correction learning). Never null. */
    public final List<String> sourceLines;

    public ReceiptDraft(@Nullable String merchantForComment,
                        @Nullable Double totalAmount,
                        @Nullable String dateYyyyMmDd,
                        float amountConfidence,
                        @Nullable String rawOcrSample) {
        this(merchantForComment, totalAmount, dateYyyyMmDd, amountConfidence, rawOcrSample,
                Collections.<String>emptyList());
    }

    public ReceiptDraft(@Nullable String merchantForComment,
                        @Nullable Double totalAmount,
                        @Nullable String dateYyyyMmDd,
                        float amountConfidence,
                        @Nullable String rawOcrSample,
                        @Nullable List<String> sourceLines) {
        this.merchantForComment = merchantForComment;
        this.totalAmount = totalAmount;
        this.dateYyyyMmDd = dateYyyyMmDd;
        this.amountConfidence = amountConfidence;
        this.rawOcrSample = rawOcrSample;
        if (sourceLines == null || sourceLines.isEmpty()) {
            this.sourceLines = Collections.emptyList();
        } else {
            this.sourceLines = Collections.unmodifiableList(sourceLines);
        }
    }
}
