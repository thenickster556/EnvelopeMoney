package com.example.envelopemoney.receipt;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReceiptPreviewToolbarMetricsTest {

    @Test
    public void iconSize_isLargerThanPacked48() {
        assertTrue(ReceiptPreviewToolbarMetrics.ICON_SIZE_DP > 48);
        assertEquals(56, ReceiptPreviewToolbarMetrics.ICON_SIZE_DP);
    }

    @Test
    public void gap_separatesAdjacentIcons() {
        assertTrue(ReceiptPreviewToolbarMetrics.ICON_GAP_DP >= 8);
        assertEquals(8, ReceiptPreviewToolbarMetrics.ICON_GAP_DP);
    }

    @Test
    public void attachedRow_fits320dpPhone() {
        int horizontalPadding = 8;
        int close = ReceiptPreviewToolbarMetrics.ICON_SIZE_DP;
        int gapBeforeActions = ReceiptPreviewToolbarMetrics.ICON_GAP_DP;
        int fourActions = 4 * ReceiptPreviewToolbarMetrics.ICON_SIZE_DP;
        int threeGutters = 3 * ReceiptPreviewToolbarMetrics.ICON_GAP_DP;
        assertEquals(320, horizontalPadding + close + gapBeforeActions + fourActions + threeGutters);
    }
}
