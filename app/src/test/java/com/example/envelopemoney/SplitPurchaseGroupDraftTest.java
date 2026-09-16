package com.example.envelopemoney;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SplitPurchaseGroupDraftTest {

    @Test
    public void validate_rejectsFewerThanTwoSlices() {
        TransferGroupValidationResult r = SplitPurchaseGroupDraft.validate(10d,
                Collections.singletonList(new SplitPurchaseSliceAllocation("a", "Fun", 10d)));
        assertFalse(r.isValid());
    }

    @Test
    public void validate_rejectsDuplicatePonds() {
        List<SplitPurchaseSliceAllocation> slices = Arrays.asList(
                new SplitPurchaseSliceAllocation("1", "Fun", 5d),
                new SplitPurchaseSliceAllocation("2", "fun", 5d));
        TransferGroupValidationResult r = SplitPurchaseGroupDraft.validate(10d, slices);
        assertFalse(r.isValid());
    }

    @Test
    public void validate_rejectsSumMismatch() {
        List<SplitPurchaseSliceAllocation> slices = Arrays.asList(
                new SplitPurchaseSliceAllocation("1", "Fun", 5d),
                new SplitPurchaseSliceAllocation("2", "Edu", 4d));
        TransferGroupValidationResult r = SplitPurchaseGroupDraft.validate(10d, slices);
        assertFalse(r.isValid());
    }

    @Test
    public void validate_acceptsExactSum() {
        List<SplitPurchaseSliceAllocation> slices = Arrays.asList(
                new SplitPurchaseSliceAllocation("1", "Fun", 5d),
                new SplitPurchaseSliceAllocation("2", "Edu", 5d));
        TransferGroupValidationResult r = SplitPurchaseGroupDraft.validate(10d, slices);
        assertTrue(r.isValid());
    }
}
