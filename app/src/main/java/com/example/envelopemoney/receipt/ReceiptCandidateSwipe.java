package com.example.envelopemoney.receipt;

/**
 * Classifies a one-finger horizontal swipe at fit-scale for candidate browsing.
 * Finger left is next; finger right is previous. The helper does not know list bounds.
 */
final class ReceiptCandidateSwipe {
    static final float MIN_DISTANCE_DP = 48f;

    enum Direction { NONE, PREVIOUS, NEXT }

    private ReceiptCandidateSwipe() {}

    static Direction decide(float dx, float dy, float density,
            boolean atFit, boolean scaleInProgress, int pointerCount) {
        if (!atFit || scaleInProgress || pointerCount != 1) {
            return Direction.NONE;
        }
        if (density <= 0f) {
            return Direction.NONE;
        }
        float minPx = MIN_DISTANCE_DP * density;
        if (Math.abs(dx) < minPx) {
            return Direction.NONE;
        }
        if (Math.abs(dx) <= Math.abs(dy)) {
            return Direction.NONE;
        }
        return dx < 0f ? Direction.NEXT : Direction.PREVIOUS;
    }

    static int indexDelta(Direction direction) {
        if (direction == Direction.NEXT) {
            return 1;
        }
        if (direction == Direction.PREVIOUS) {
            return -1;
        }
        return 0;
    }
}
