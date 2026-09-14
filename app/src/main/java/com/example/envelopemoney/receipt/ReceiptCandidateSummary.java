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
        double cents = MoneyMath.roundToCents(amount);
        String sign = cents < 0 ? "-" : "";
        return title + " · " + sign + String.format(Locale.US, "$%.2f", Math.abs(cents));
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
