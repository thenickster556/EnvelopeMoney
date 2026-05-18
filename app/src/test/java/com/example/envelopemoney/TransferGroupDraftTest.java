package com.example.envelopemoney;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransferGroupDraftTest {

    @Test
    public void allocatedTotal_andSpentInSource_areCalculated() {
        assertEquals(12.5d, TransferGroupDraft.allocatedTotal(Arrays.asList(
                new TransferBucketAllocation("a", "Savings", 8.0d),
                new TransferBucketAllocation("b", "Vacation", 4.5d))), 0.0001d);
        assertEquals(7.5d, TransferGroupDraft.spentInSource(20.0d, Arrays.asList(
                new TransferBucketAllocation("a", "Savings", 8.0d),
                new TransferBucketAllocation("b", "Vacation", 4.5d))), 0.0001d);
    }

    @Test
    public void validate_rejectsDuplicateDestinations() {
        TransferGroupValidationResult result = TransferGroupDraft.validate(20.0d, "Groceries", Arrays.asList(
                new TransferBucketAllocation("a", "Savings", 8.0d),
                new TransferBucketAllocation("b", "Savings", 4.5d)));

        assertFalse(result.isValid());
        assertEquals("Each destination can only appear once per transfer", result.getMessage());
    }

    @Test
    public void validate_rejectsOverAllocation() {
        TransferGroupValidationResult result = TransferGroupDraft.validate(10.0d, "Groceries", Arrays.asList(
                new TransferBucketAllocation("a", "Savings", 8.0d),
                new TransferBucketAllocation("b", "Vacation", 4.5d)));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("Transfers exceed the total"));
    }

    @Test
    public void validate_acceptsUniquePositiveAllocations() {
        TransferGroupValidationResult result = TransferGroupDraft.validate(20.0d, "Groceries", Arrays.asList(
                new TransferBucketAllocation("a", "Savings", 8.0d),
                new TransferBucketAllocation("b", "Vacation", 4.5d)));

        assertTrue(result.isValid());
    }

    @Test
    public void validate_acceptsExactCentManualAmounts() {
        TransferGroupValidationResult result = TransferGroupDraft.validate(20.0d, "Groceries", Arrays.asList(
                new TransferBucketAllocation("a", "Savings", 4.37d),
                new TransferBucketAllocation("b", "Vacation", 3.13d)));

        assertTrue(result.isValid());
    }

    @Test
    public void validate_requiresAtLeastOneBucket() {
        TransferGroupValidationResult result = TransferGroupDraft.validate(20.0d, "Groceries", Collections.emptyList());

        assertFalse(result.isValid());
        assertEquals("Add at least one transfer bucket", result.getMessage());
    }
}
