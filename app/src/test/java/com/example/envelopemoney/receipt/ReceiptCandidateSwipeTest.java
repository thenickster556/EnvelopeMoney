package com.example.envelopemoney.receipt;

import org.junit.Test;

import static com.example.envelopemoney.receipt.ReceiptCandidateSwipe.Direction.NEXT;
import static com.example.envelopemoney.receipt.ReceiptCandidateSwipe.Direction.NONE;
import static com.example.envelopemoney.receipt.ReceiptCandidateSwipe.Direction.PREVIOUS;
import static com.example.envelopemoney.receipt.ReceiptCandidateSwipe.decide;
import static com.example.envelopemoney.receipt.ReceiptCandidateSwipe.indexDelta;
import static org.junit.Assert.assertEquals;

public class ReceiptCandidateSwipeTest {

    @Test
    public void swipeLeftAtFit_isNext() {
        assertEquals(NEXT, decide(-150f, 10f, 3f, true, false, 1));
    }

    @Test
    public void swipeRightAtFit_isPrevious() {
        assertEquals(PREVIOUS, decide(200f, 20f, 3f, true, false, 1));
    }

    @Test
    public void belowThreshold_isNone() {
        assertEquals(NONE, decide(-100f, 10f, 3f, true, false, 1));
    }

    @Test
    public void exactlyMinPxHorizontal_isDirection() {
        assertEquals(NEXT, decide(-144f, 0f, 3f, true, false, 1));
    }

    @Test
    public void verticalDominant_isNone() {
        assertEquals(NONE, decide(-200f, -200f, 3f, true, false, 1));
    }

    @Test
    public void zoomed_isNone() {
        assertEquals(NONE, decide(-200f, 10f, 3f, false, false, 1));
    }

    @Test
    public void pinchInProgress_isNone() {
        assertEquals(NONE, decide(-200f, 10f, 3f, true, true, 1));
    }

    @Test
    public void twoPointers_isNone() {
        assertEquals(NONE, decide(-200f, 10f, 3f, true, false, 2));
    }

    @Test
    public void badDensity_isNone() {
        assertEquals(NONE, decide(-200f, 10f, 0f, true, false, 1));
    }

    @Test
    public void indexDelta_matchesGallery() {
        assertEquals(1, indexDelta(NEXT));
        assertEquals(-1, indexDelta(PREVIOUS));
        assertEquals(0, indexDelta(NONE));
    }
}
