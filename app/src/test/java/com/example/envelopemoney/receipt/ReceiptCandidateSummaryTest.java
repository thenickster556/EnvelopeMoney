package com.example.envelopemoney.receipt;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReceiptCandidateSummaryTest {

    @Test
    public void titlePrefersCommentAndFormatsCents() {
        assertEquals("Lunch · $12.50",
                ReceiptCandidateSummary.titleLine("Lunch", "Food", 12.5));
        assertEquals("Food · $4.00",
                ReceiptCandidateSummary.titleLine(null, "Food", 4));
        assertEquals("Food · $4.00",
                ReceiptCandidateSummary.titleLine("   ", "Food", 4));
        assertEquals("Refund · -$3.20",
                ReceiptCandidateSummary.titleLine("Refund", "Food", -3.2));
    }

    @Test
    public void detailShowsPondOnlyWhenCommentNamedSomethingElse() {
        assertEquals("Food · Sep 7, 2026",
                ReceiptCandidateSummary.detailLine("Lunch", "Food", "2026-09-07"));
        assertEquals("Sep 7, 2026",
                ReceiptCandidateSummary.detailLine(null, "Food", "2026-09-07"));
        assertEquals("Food · not-a-date",
                ReceiptCandidateSummary.detailLine("Lunch", "Food", "not-a-date"));
    }
}
