package com.example.envelopemoney.receipt;

import android.view.View;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReceiptPreviewImmersiveTest {

    @Test
    public void immersive_hidesChromeAndShowsExit() {
        assertEquals(View.GONE, ReceiptPreviewImmersive.topChromeVisibility(true));
        assertEquals(View.GONE, ReceiptPreviewImmersive.candidateBottomVisibility(true, true));
        assertEquals(View.VISIBLE, ReceiptPreviewImmersive.exitButtonVisibility(true));
    }

    @Test
    public void restore_showsChromeAndHidesExit() {
        assertEquals(View.VISIBLE, ReceiptPreviewImmersive.topChromeVisibility(false));
        assertEquals(View.VISIBLE, ReceiptPreviewImmersive.candidateBottomVisibility(false, true));
        assertEquals(View.GONE, ReceiptPreviewImmersive.exitButtonVisibility(false));
    }

    @Test
    public void attachedMode_keepsBottomBarGone() {
        assertEquals(View.GONE, ReceiptPreviewImmersive.candidateBottomVisibility(false, false));
        assertEquals(View.GONE, ReceiptPreviewImmersive.candidateBottomVisibility(true, false));
    }

    @Test
    public void back_exitsImmersiveFirst() {
        assertTrue(ReceiptPreviewImmersive.consumeBack(true));
        assertFalse(ReceiptPreviewImmersive.consumeBack(false));
    }

    @Test
    public void cue_visibleOnlyWhenImmersiveAndCandidate() {
        assertEquals(View.VISIBLE, ReceiptPreviewImmersive.cueVisibility(true, true));
        assertEquals(View.GONE, ReceiptPreviewImmersive.cueVisibility(false, true));
        assertEquals(View.GONE, ReceiptPreviewImmersive.cueVisibility(true, false));
        assertEquals(View.GONE, ReceiptPreviewImmersive.cueVisibility(false, false));
    }
}
