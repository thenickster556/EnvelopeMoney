package com.example.envelopemoney.receipt;

import com.example.envelopemoney.*;
import com.google.gson.Gson;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ReceiptReferenceRepairTest {
    private static final String OLD = "content://media/external/images/media/1";
    private static final String FRESH = "content://media/external/images/media/2";
    private Transaction yesterday() {
        Transaction transaction = new Transaction("Food", 12.50, "2026-09-07", "Lunch");
        transaction.setReceiptImageUri(OLD);
        return transaction;
    }
    @Test public void repairsCurrentAndHistoricalSharedReferencesAndSurvivesRestart() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction current = yesterday(); Transaction historical = yesterday();
        envelope.getTransactions().add(current);
        Envelope.MonthData month = new Envelope.MonthData(100, 87.5);
        month.transactions.add(historical);
        envelope.getMonthlyDataMap().put("2026-09", month);
        List<Envelope> envelopes = Arrays.asList(envelope);
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(envelopes);
        assertEquals(2, entries.size());
        Map<String, ReceiptReferenceResolver.Result> results = new HashMap<>();
        for (ReceiptReferenceRepair.Entry entry : entries) results.put(entry.key(), ReceiptReferenceResolver.Result.resolved(FRESH, "yesterday.jpg"));
        assertEquals(2, ReceiptReferenceRepair.apply(envelopes, entries, results));
        assertEquals(0, ReceiptReferenceRepair.apply(envelopes, entries, results));
        Envelope restored = new Gson().fromJson(new Gson().toJson(envelope), Envelope.class);
        assertEquals(FRESH, restored.getTransactions().get(0).getReceiptImageUri());
        assertEquals("yesterday.jpg", restored.getTransactions().get(0).getReceiptImageFileName());
        assertEquals(FRESH, restored.getMonthlyDataMap().get("2026-09").transactions.get(0).getReceiptImageUri());
        assertEquals(12.50, current.getAmount(), 0);
    }
    @Test public void concurrentReplacementDeletionAndFailuresArePreserved() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction changed = yesterday(); Transaction removed = yesterday(); Transaction failed = yesterday();
        envelope.getTransactions().addAll(Arrays.asList(changed, removed, failed));
        List<Envelope> envelopes = Arrays.asList(envelope);
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(envelopes);
        changed.setReceiptImageUri("content://replacement/3");
        envelope.getTransactions().remove(removed);
        Map<String, ReceiptReferenceResolver.Result> results = new HashMap<>();
        results.put(entries.get(0).key(), ReceiptReferenceResolver.Result.failure(ReceiptReferenceResolver.Status.MISSING));
        assertEquals(0, ReceiptReferenceRepair.apply(envelopes, entries, results));
        results.put(entries.get(0).key(), ReceiptReferenceResolver.Result.resolved(FRESH, "old.jpg"));
        assertEquals(1, ReceiptReferenceRepair.apply(envelopes, entries, results));
        assertEquals("content://replacement/3", changed.getReceiptImageUri());
        assertEquals(OLD, removed.getReceiptImageUri());
    }
    @Test public void emptyStateAndRepeatedObjectAreSafe() {
        assertTrue(ReceiptReferenceRepair.snapshot(null).isEmpty());
        Envelope envelope = new Envelope("Food", 100);
        Transaction transaction = yesterday();
        envelope.getTransactions().addAll(Arrays.asList(null, transaction, transaction,
                new Transaction("Food", 1, "2026-09-08", "No picture")));
        assertEquals(1, ReceiptReferenceRepair.snapshot(Arrays.asList(null, envelope)).size());
        assertEquals(0, ReceiptReferenceRepair.apply(Arrays.asList(envelope),
                ReceiptReferenceRepair.snapshot(Arrays.asList(envelope)), Collections.emptyMap()));
    }

    @Test public void filenameSurvivesTransferMirrorsAndSplitGroupEdits() {
        Envelope food = new Envelope("Food", 100);
        Envelope savings = new Envelope("Savings", 100);
        List<Envelope> envelopes = Arrays.asList(food, savings);
        Transaction source = yesterday(); source.setReceiptImageFileName("yesterday.jpg");
        food.getTransactions().add(source);
        TransferSyncHelper.applyTransferGroup(envelopes, source, "Food", Arrays.asList(
                new TransferBucketAllocation("bucket", "Savings", 2)));
        assertEquals("yesterday.jpg", savings.getTransactions().get(0).getReceiptImageFileName());
        List<SplitPurchaseSliceAllocation> slices = Arrays.asList(
                new SplitPurchaseSliceAllocation("food", "Food", 5),
                new SplitPurchaseSliceAllocation("savings", "Savings", 5));
        SplitPurchaseSyncHelper.applyGroup(envelopes, "split", "2026-09-07", "Lunch", OLD, slices, "2026-09");
        for (Transaction transaction : food.getTransactions()) {
            if ("split".equals(transaction.getSplitPurchaseGroupId())) transaction.setReceiptImageFileName("yesterday.jpg");
        }
        SplitPurchaseSyncHelper.applyGroup(envelopes, "split", "2026-09-07", "Lunch edited", OLD, slices, "2026-09");
        for (Envelope envelope : envelopes) {
            for (Transaction transaction : envelope.getTransactions()) {
                if ("split".equals(transaction.getSplitPurchaseGroupId())) assertEquals("yesterday.jpg", transaction.getReceiptImageFileName());
            }
        }
    }

    @Test public void replacingOrRemovingPictureClearsOldFilename() {
        Transaction transaction = yesterday(); transaction.setReceiptImageFileName("old.jpg");
        transaction.setReceiptImageUri(OLD);
        assertEquals("old.jpg", transaction.getReceiptImageFileName());
        transaction.setReceiptImageUri(FRESH);
        assertNull(transaction.getReceiptImageFileName());
        transaction.setReceiptImageUri(FRESH + "#new.jpg");
        assertEquals("new.jpg", transaction.getReceiptImageFileName());
        transaction.setReceiptImageUri(null);
        assertNull(transaction.getReceiptImageFileName());
    }
}
