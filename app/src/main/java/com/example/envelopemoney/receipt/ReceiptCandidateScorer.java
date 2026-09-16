package com.example.envelopemoney.receipt;

import com.example.envelopemoney.MoneyMath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Content match between one candidate receipt's OCR and its transaction. Pure arithmetic over
 * primitives so ranking stays testable without a device; higher is better, and equal scores keep
 * the incoming (capture-rank) order.
 */
public final class ReceiptCandidateScorer {

    public static final int AMOUNT_EXACT = 100;
    public static final int AMOUNT_CLOSE = 40;
    /** Printed total is farther than close but still near enough to append to the shortlist. */
    public static final int AMOUNT_NEAR = 15;
    public static final int MERCHANT_PER_TOKEN = 25;
    public static final int MERCHANT_TOKEN_CAP = 50;
    public static final int DATE_MATCH = 20;

    /** Amount within this absolute difference still counts as close (tier 40). */
    private static final double CLOSE_ABSOLUTE_TOLERANCE = 2.00;
    /** Or within this fraction of the transaction amount, whichever is larger. */
    private static final double CLOSE_FRACTION_TOLERANCE = 0.10;
    /** Amount within this absolute difference still counts as near (tier 15). */
    private static final double NEAR_ABSOLUTE_TOLERANCE = 5.00;
    /** Or within this fraction of the transaction amount, whichever is larger. */
    private static final double NEAR_FRACTION_TOLERANCE = 0.25;
    private static final int MINIMUM_MERCHANT_TOKEN_LENGTH = 3;

    private ReceiptCandidateScorer() {
    }

    /**
     * @param ocrTotal        total parsed from the candidate picture; null when nothing was read
     * @param ocrMerchant     merchant/comment parsed from the candidate picture
     * @param ocrDate         yyyy-MM-dd parsed from the candidate picture
     * @param transactionAmount amount stored on the transaction
     * @param transactionComment comment stored on the transaction (may carry the merchant)
     * @param transactionDate  yyyy-MM-dd stored on the transaction
     */
    public static int score(Double ocrTotal, String ocrMerchant, String ocrDate,
                            double transactionAmount, String transactionComment,
                            String transactionDate) {
        return amountTier(ocrTotal, transactionAmount)
                + merchantScore(ocrMerchant, transactionComment)
                + dateScore(ocrDate, transactionDate);
    }

    /**
     * Amount-only band shared by ranking, leftover append, and chooser badges.
     * Exact cents → 100; within $2 or 10% → 40; within $5 or 25% → 15; else 0.
     */
    public static int amountTier(Double ocrTotal, double transactionAmount) {
        if (ocrTotal == null) return 0;
        double difference = Math.abs(MoneyMath.roundToCents(ocrTotal)
                - MoneyMath.roundToCents(transactionAmount));
        if (difference == 0d) return AMOUNT_EXACT;
        double closeTolerance = Math.max(CLOSE_ABSOLUTE_TOLERANCE,
                CLOSE_FRACTION_TOLERANCE * Math.abs(transactionAmount));
        if (difference <= closeTolerance) return AMOUNT_CLOSE;
        double nearTolerance = Math.max(NEAR_ABSOLUTE_TOLERANCE,
                NEAR_FRACTION_TOLERANCE * Math.abs(transactionAmount));
        return difference <= nearTolerance ? AMOUNT_NEAR : 0;
    }

    /**
     * Leftover unused pictures whose printed total is exact, close, or near join the ±1 shortlist.
     * A miss (including no OCR total) waits for See all unused pictures.
     */
    public static boolean shouldAppendByAmount(Double ocrTotal, double transactionAmount) {
        return amountTier(ocrTotal, transactionAmount) >= AMOUNT_NEAR;
    }

    private static int merchantScore(String ocrMerchant, String transactionComment) {
        if (ocrMerchant == null || transactionComment == null) return 0;
        Set<String> commentTokens = tokens(transactionComment);
        int matches = 0;
        for (String token : tokens(ocrMerchant)) {
            if (commentTokens.contains(token)) matches++;
        }
        int capped = Math.min(matches, MERCHANT_TOKEN_CAP / MERCHANT_PER_TOKEN);
        return capped * MERCHANT_PER_TOKEN;
    }

    private static int dateScore(String ocrDate, String transactionDate) {
        if (ocrDate == null || transactionDate == null) return 0;
        return ocrDate.trim().equals(transactionDate.trim()) ? DATE_MATCH : 0;
    }

    /** Lowercase alphanumeric tokens of at least three characters; punctuation never matches. */
    private static Set<String> tokens(String text) {
        Set<String> tokens = new HashSet<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            boolean letterOrDigit = i < text.length()
                    && Character.isLetterOrDigit(text.charAt(i));
            if (letterOrDigit) {
                current.append(Character.toLowerCase(text.charAt(i)));
                continue;
            }
            if (current.length() >= MINIMUM_MERCHANT_TOKEN_LENGTH) {
                tokens.add(current.toString());
            }
            current.setLength(0);
        }
        return tokens;
    }

    /** Score lookup for {@link #sortByScoreDesc}; a custom type keeps minSdk 21 compatibility. */
    public interface ScoreLookup<T> {
        int score(T item);
    }

    /** Descending by score; equal scores keep the incoming order (stable capture rank). */
    public static <T> List<T> sortByScoreDesc(List<T> items, ScoreLookup<T> score) {
        List<T> ranked = new ArrayList<>(items);
        Comparator<T> byScoreDescending = new Comparator<T>() {
            @Override
            public int compare(T left, T right) {
                return Integer.compare(score.score(right), score.score(left));
            }
        };
        java.util.Collections.sort(ranked, byScoreDescending);
        return ranked;
    }

    /** Locale-stable helper for badge text. */
    public static String formatAmount(double amount) {
        double cents = MoneyMath.roundToCents(amount);
        String sign = cents < 0 ? "-" : "";
        return sign + String.format(Locale.US, "$%.2f", Math.abs(cents));
    }
}
