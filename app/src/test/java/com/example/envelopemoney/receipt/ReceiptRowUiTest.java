package com.example.envelopemoney.receipt;

import com.example.envelopemoney.Transaction;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReceiptRowUiTest {

    @Test
    public void showReceiptThumbnail_falseWhenNoUri() {
        Transaction t = new Transaction("Food", 1.0, "2026-04-01", "x");
        assertFalse(ReceiptRowUi.showReceiptThumbnail(t));
    }

    @Test
    public void showReceiptThumbnail_trueWhenUriSet() {
        Transaction t = new Transaction("Food", 1.0, "2026-04-01", "x");
        t.setReceiptImageUri("content://example/1");
        assertTrue(ReceiptRowUi.showReceiptThumbnail(t));
    }

    @Test
    public void showReceiptThumbnail_falseForPlaceholderRow() {
        Transaction t = new Transaction("No transactions yet", 0, "2026-04-01", "hint");
        t.setReceiptImageUri("content://example/1");
        assertFalse(ReceiptRowUi.showReceiptThumbnail(t));
    }
}
