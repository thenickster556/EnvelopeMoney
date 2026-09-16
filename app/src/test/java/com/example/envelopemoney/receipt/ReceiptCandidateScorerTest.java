package com.example.envelopemoney.receipt;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ReceiptCandidateScorerTest {

    @Test
    public void pinnedExampleScores() {
        // 100 exact amount + 25 one merchant token + 20 date
        assertEquals(145, ReceiptCandidateScorer.score(
                12.50, "KROGER", "2026-09-07", 12.50, "Kroger run", "2026-09-07"));
        // 40 close amount + 25 merchant + 20 date
        assertEquals(85, ReceiptCandidateScorer.score(
                12.75, "KROGER", "2026-09-07", 12.50, "Kroger run", "2026-09-07"));
        // No amount; one merchant token + date
        assertEquals(45, ReceiptCandidateScorer.score(
                null, "SHELL", "2026-09-07", 12.50, "Shell", "2026-09-07"));
        assertEquals(0, ReceiptCandidateScorer.score(
                null, null, null, 12.50, "Kroger run", "2026-09-07"));
    }

    @Test
    public void amountTiersAreExactCloseThenMiss() {
        assertEquals(ReceiptCandidateScorer.AMOUNT_EXACT, ReceiptCandidateScorer.score(
                12.50, null, null, 12.50, null, null));
        assertEquals(ReceiptCandidateScorer.AMOUNT_CLOSE, ReceiptCandidateScorer.score(
                12.75, null, null, 12.50, null, null));
        // 2% tolerance on a large total still counts as close.
        assertEquals(ReceiptCandidateScorer.AMOUNT_CLOSE, ReceiptCandidateScorer.score(
                101.90, null, null, 100.00, null, null));
        assertEquals(0, ReceiptCandidateScorer.score(
                30.00, null, null, 12.50, null, null));
    }

    @Test
    public void merchantMatchIsNormalizedAndCapped() {
        assertEquals(2 * ReceiptCandidateScorer.MERCHANT_PER_TOKEN, ReceiptCandidateScorer.score(
                null, "KROGER #427", null, 99.0, "kroger 427 fuel", null));
        assertEquals(ReceiptCandidateScorer.MERCHANT_TOKEN_CAP, ReceiptCandidateScorer.score(
                null, "shell kroger walmart target costco", null,
                99.0, "kroger walmart target costco shell", null));
        // Two-char noise tokens never match.
        assertEquals(0, ReceiptCandidateScorer.score(
                null, "at", null, 99.0, "at at at", null));
    }

    @Test
    public void nullInputsAreSafe() {
        assertEquals(0, ReceiptCandidateScorer.score(null, null, null, 12.50, null, null));
        assertEquals(ReceiptCandidateScorer.AMOUNT_EXACT, ReceiptCandidateScorer.score(
                12.50, null, null, 12.50, null, null));
        assertEquals(ReceiptCandidateScorer.DATE_MATCH, ReceiptCandidateScorer.score(
                null, null, "2026-09-07", 1.0, null, "2026-09-07"));
    }

    @Test
    public void stableSortKeepsCaptureRankForEqualScores() {
        List<String> captureOrder = new ArrayList<>(Arrays.asList("a", "b", "c"));
        List<String> ranked = ReceiptCandidateScorer.sortByScoreDesc(
                captureOrder, candidate -> candidate.equals("c") ? 100 : 0);
        assertEquals(Arrays.asList("c", "a", "b"), ranked);
    }
}
