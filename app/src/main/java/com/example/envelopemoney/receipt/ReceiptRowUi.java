package com.example.envelopemoney.receipt;

import com.example.envelopemoney.Transaction;

/**
 * Pure helpers for when to show receipt affordances in lists.
 */
public final class ReceiptRowUi {
    private static final String PLACEHOLDER_ENVELOPE = "No transactions yet";

    private ReceiptRowUi() {
    }

    public static boolean showReceiptThumbnail(Transaction transaction) {
        if (transaction == null) {
            return false;
        }
        if (PLACEHOLDER_ENVELOPE.equals(transaction.getEnvelopeName())) {
            return false;
        }
        String uri = transaction.getReceiptImageUri();
        return uri != null && !uri.isEmpty();
    }
}
