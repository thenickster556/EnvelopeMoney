package com.example.envelopemoney;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TransferSyncHelperTest {

    @Test
    public void getAllocations_migratesLegacySingleTransfer() {
        Envelope groceries = new Envelope("Groceries", 100d);
        groceries.addTransfer("transfer-1", "Savings", 8.0d);
        Transaction source = new Transaction("Groceries", 20.0d, "2026-05-11", "Groceries");
        source.setTransferId("transfer-1");
        groceries.getTransactions().add(source);

        List<TransferBucketAllocation> allocations = TransferSyncHelper.getAllocations(
                Arrays.asList(groceries, new Envelope("Savings", 0d)),
                "transfer-1");

        assertEquals(1, allocations.size());
        assertEquals("Savings", allocations.get(0).getToEnvelope());
        assertNotNull(allocations.get(0).getBucketId());
    }

    @Test
    public void applyTransferGroup_createsMirrorTransactionsPerBucket() {
        Envelope groceries = new Envelope("Groceries", 100d);
        Envelope savings = new Envelope("Savings", 100d);
        Envelope vacation = new Envelope("Vacation", 100d);
        List<Envelope> envelopes = Arrays.asList(groceries, savings, vacation);

        Transaction source = new Transaction("Groceries", 20.0d, "2026-05-11", "Dinner");
        groceries.getTransactions().add(source);

        TransferSyncHelper.applyTransferGroup(envelopes, source, "Groceries", Arrays.asList(
                new TransferBucketAllocation("bucket-1", "Savings", 8.0d),
                new TransferBucketAllocation("bucket-2", "Vacation", 4.5d)
        ));

        assertNotNull(source.getTransferId());
        assertNull(source.getTransferBucketId());
        assertEquals(2, groceries.getTransfers().size());
        assertEquals(1, savings.getTransactions().size());
        assertEquals(-8.0d, savings.getTransactions().get(0).getAmount(), 0.0001d);
        assertEquals("bucket-1", savings.getTransactions().get(0).getTransferBucketId());
        assertEquals(1, vacation.getTransactions().size());
        assertEquals(-4.5d, vacation.getTransactions().get(0).getAmount(), 0.0001d);
        assertEquals("bucket-2", vacation.getTransactions().get(0).getTransferBucketId());
    }

    @Test
    public void detachTransferGroup_removesMirrorsAndClearsSourceLinkage() {
        Envelope groceries = new Envelope("Groceries", 100d);
        Envelope savings = new Envelope("Savings", 100d);
        List<Envelope> envelopes = Arrays.asList(groceries, savings);

        Transaction source = new Transaction("Groceries", 20.0d, "2026-05-11", "Groceries");
        groceries.getTransactions().add(source);
        TransferSyncHelper.applyTransferGroup(envelopes, source, "Groceries", Arrays.asList(
                new TransferBucketAllocation("bucket-1", "Savings", 8.0d)
        ));

        TransferSyncHelper.detachTransferGroup(envelopes, source);

        assertNull(source.getTransferId());
        assertNull(source.getTransferBucketId());
        assertEquals(0, groceries.getTransfers().size());
        assertEquals(0, savings.getTransactions().size());
    }

    @Test
    public void resolveAnchorTransaction_returnsSourceSummaryForMirrorRow() {
        Envelope groceries = new Envelope("Groceries", 100d);
        Envelope savings = new Envelope("Savings", 100d);
        List<Envelope> envelopes = new ArrayList<>(Arrays.asList(groceries, savings));

        Transaction source = new Transaction("Groceries", 20.0d, "2026-05-11", "Groceries");
        groceries.getTransactions().add(source);
        TransferSyncHelper.applyTransferGroup(envelopes, source, "Groceries", Arrays.asList(
                new TransferBucketAllocation("bucket-1", "Savings", 8.0d)
        ));

        Transaction mirror = savings.getTransactions().get(0);

        assertEquals(source, TransferSyncHelper.resolveAnchorTransaction(envelopes, mirror));
    }
}
