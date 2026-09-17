package com.example.envelopemoney.receipt;

import com.example.envelopemoney.MoneyMath;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Transaction context lines for the candidate check header and picker message. Pure formatting,
 * no Android types, never throws: unparseable dates pass through unchanged.
 */
public final class ReceiptCandidateSummary {
    private ReceiptCandidateSummary() {
    }

    /** "Lunch · $12.50" — comment first, pond name when there is no comment; negatives read "-$3.20". */
    public static String titleLine(String comment, String envelopeName, double amount) {
        String title = comment != null && !comment.trim().isEmpty() ? comment.trim() : envelopeName;
        return title + " · " + formatMoney(amount);
    }

    /** Immersive cue line 1. Empty comments use {@code emptyFallback} so the three-line layout stays stable. */
    public static String immersiveComment(String comment, String emptyFallback) {
        if (comment != null && !comment.trim().isEmpty()) {
            return comment.trim();
        }
        return emptyFallback != null ? emptyFallback : "";
    }

    /** Immersive cue line 2. Pond is always shown, unlike {@link #detailLine}. */
    public static String immersivePond(String envelopeName) {
        return envelopeName != null ? envelopeName : "";
    }

    /** Immersive cue line 3. Same money rules as {@link #titleLine}. */
    public static String immersiveAmount(double amount) {
        return formatMoney(amount);
    }

    /**
     * Chooser dialog subtitle: comment/pond, fund amount, and transaction date. Used for the
     * shortlist and for See all unused pictures so OCR totals can be cross-checked.
     */
    public static String chooserDialogSummary(String comment, String envelopeName, double amount,
                                              String yyyyMmDdDate) {
        String title = titleLine(comment, envelopeName, amount);
        String detail = detailLine(comment, envelopeName, yyyyMmDdDate);
        if (title == null || title.isEmpty()) {
            return detail != null ? detail : "";
        }
        if (detail == null || detail.isEmpty()) {
            return title;
        }
        return title + " · " + detail;
    }

    /**
     * Headline after See all unused pictures. Keeps {@code transactionSummary} (amount and date).
     */
    public static String seeAllUnusedHeadline(int pictureCount, String transactionSummary) {
        String summary = transactionSummary == null ? "" : transactionSummary;
        return pictureCount + " unused pictures for this receipt (" + summary
                + "). OCR shows the printed $";
    }

    private static String formatMoney(double amount) {
        double cents = MoneyMath.roundToCents(amount);
        String sign = cents < 0 ? "-" : "";
        return sign + String.format(Locale.US, "$%.2f", Math.abs(cents));
    }

    /** "Food · Sep 7, 2026" — pond only when the comment already named something else. */
    public static String detailLine(String comment, String envelopeName, String yyyyMmDdDate) {
        boolean hasComment = comment != null && !comment.trim().isEmpty();
        String date = displayDate(yyyyMmDdDate);
        return hasComment ? envelopeName + " · " + date : date;
    }

    private static String displayDate(String yyyyMmDdDate) {
        if (yyyyMmDdDate == null || yyyyMmDdDate.trim().isEmpty()) return "";
        SimpleDateFormat stored = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        stored.setLenient(false);
        try {
            Date parsed = stored.parse(yyyyMmDdDate.trim());
            if (parsed == null) return yyyyMmDdDate;
            return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed);
        } catch (ParseException invalidDate) {
            return yyyyMmDdDate;
        }
    }
}
