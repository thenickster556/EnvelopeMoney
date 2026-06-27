package com.example.envelopemoney.receipt;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReceiptDateFilterHelperTest {

    @Test
    public void isIsoDateOutsideFilterRange_falseWhenInside() {
        assertFalse(ReceiptDateFilterHelper.isIsoDateOutsideFilterRange(
                "2026-06-15", "Jun 1, 2026", "Jun 30, 2026"));
    }

    @Test
    public void isIsoDateOutsideFilterRange_trueWhenBeforeStart() {
        assertTrue(ReceiptDateFilterHelper.isIsoDateOutsideFilterRange(
                "2024-03-15", "Jun 1, 2026", "Jun 30, 2026"));
    }

    @Test
    public void isIsoDateOutsideFilterRange_trueWhenAfterEnd() {
        assertTrue(ReceiptDateFilterHelper.isIsoDateOutsideFilterRange(
                "2026-07-01", "Jun 1, 2026", "Jun 30, 2026"));
    }

    @Test
    public void isIsoDateOutsideFilterRange_falseForNullOrBlankDate() {
        assertFalse(ReceiptDateFilterHelper.isIsoDateOutsideFilterRange(null, "Jun 1, 2026", "Jun 30, 2026"));
        assertFalse(ReceiptDateFilterHelper.isIsoDateOutsideFilterRange("", "Jun 1, 2026", "Jun 30, 2026"));
    }
}
