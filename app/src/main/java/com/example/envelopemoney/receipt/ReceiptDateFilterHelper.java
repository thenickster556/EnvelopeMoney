package com.example.envelopemoney.receipt;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Pure helper: whether an ISO receipt date falls outside the main screen's Start/End filter range.
 */
public final class ReceiptDateFilterHelper {

    private ReceiptDateFilterHelper() {
    }

    public static boolean isIsoDateOutsideFilterRange(@Nullable String isoDateYyyyMmDd,
                                                      @Nullable String filterStartDisplay,
                                                      @Nullable String filterEndDisplay) {
        if (isoDateYyyyMmDd == null || isoDateYyyyMmDd.trim().isEmpty()) {
            return false;
        }
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat display = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        try {
            Date receiptDate = iso.parse(isoDateYyyyMmDd.trim());
            if (receiptDate == null) {
                return false;
            }
            Date start = filterStartDisplay != null ? display.parse(filterStartDisplay.trim()) : null;
            Date end = filterEndDisplay != null ? display.parse(filterEndDisplay.trim()) : null;
            if (start != null && receiptDate.before(start)) {
                return true;
            }
            if (end != null && receiptDate.after(end)) {
                return true;
            }
            return false;
        } catch (ParseException e) {
            return false;
        }
    }
}
