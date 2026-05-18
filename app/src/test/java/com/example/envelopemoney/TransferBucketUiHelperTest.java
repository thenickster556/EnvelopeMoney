package com.example.envelopemoney;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransferBucketUiHelperTest {

    @Test
    public void snapToStep_roundsToNearestFixedTransferStep() {
        assertEquals(4.5d, TransferBucketUiHelper.snapToStep(4.37d, 0.5d, 10d), 0.0001d);
        assertEquals(10d, TransferBucketUiHelper.snapToStep(12d, 0.5d, 10d), 0.0001d);
        assertEquals(0d, TransferBucketUiHelper.snapToStep(-1d, 0.5d, 10d), 0.0001d);
    }

    @Test
    public void buildScaleLabels_returnsSnappedCurrencyAnchors() {
        assertEquals(Arrays.asList("$0", "$2.50", "$5", "$7.50", "$10"),
                TransferBucketUiHelper.buildScaleLabels(10d));
    }

    @Test
    public void shouldShowValidationMessage_waitsForInteractionOrSaveAttempt() {
        TransferGroupValidationResult invalid = TransferGroupValidationResult.invalid("Choose a destination");

        assertFalse(TransferBucketUiHelper.shouldShowValidationMessage(true, false, false, invalid));
        assertTrue(TransferBucketUiHelper.shouldShowValidationMessage(true, true, false, invalid));
        assertTrue(TransferBucketUiHelper.shouldShowValidationMessage(true, false, true, invalid));
        assertFalse(TransferBucketUiHelper.shouldShowValidationMessage(false, true, true, invalid));
        assertFalse(TransferBucketUiHelper.shouldShowValidationMessage(true, true, true,
                TransferGroupValidationResult.valid()));
    }

    @Test
    public void recommendedScaleLabelCount_compactsOnTightWidths() {
        assertEquals(3, TransferBucketUiHelper.recommendedScaleLabelCount(720, 3f));
        assertEquals(5, TransferBucketUiHelper.recommendedScaleLabelCount(1200, 3f));
    }
}
