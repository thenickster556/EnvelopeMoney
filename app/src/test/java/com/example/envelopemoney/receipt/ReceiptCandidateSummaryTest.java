package com.example.envelopemoney.receipt;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void immersiveCueShowsCommentPondAndAmountSeparately() {
        assertEquals("Lunch",
                ReceiptCandidateSummary.immersiveComment("Lunch", "No comment"));
        assertEquals("No comment",
                ReceiptCandidateSummary.immersiveComment(null, "No comment"));
        assertEquals("No comment",
                ReceiptCandidateSummary.immersiveComment("   ", "No comment"));
        assertEquals("Groceries", ReceiptCandidateSummary.immersivePond("Groceries"));
        assertEquals("", ReceiptCandidateSummary.immersivePond(null));
        assertEquals("$12.50", ReceiptCandidateSummary.immersiveAmount(12.5));
        assertEquals("-$3.20", ReceiptCandidateSummary.immersiveAmount(-3.2));
    }

    @Test
    public void chooserSummaryKeepsFundAmountAndDate() {
        assertEquals("Lunch · $12.50 · Food · Sep 7, 2026",
                ReceiptCandidateSummary.chooserDialogSummary("Lunch", "Food", 12.5, "2026-09-07"));
    }

    @Test
    public void seeAllUnusedHeadlineStillIncludesFundAmountAndDate() {
        String summary = ReceiptCandidateSummary.chooserDialogSummary("Lunch", "Food", 12.5, "2026-09-07");
        String headline = ReceiptCandidateSummary.seeAllUnusedHeadline(4, summary);
        assertTrue(headline.contains("$12.50"));
        assertTrue(headline.contains("Sep 7, 2026"));
        assertTrue(headline.contains("4 unused"));
    }
}
