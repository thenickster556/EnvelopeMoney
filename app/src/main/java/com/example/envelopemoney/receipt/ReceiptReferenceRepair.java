package com.example.envelopemoney.receipt;

import com.example.envelopemoney.Envelope;
import com.example.envelopemoney.Transaction;
import java.util.*;

/**
 * Repairs receipt associations only; amounts and gallery files are untouched. Snapshot/apply run
 * on the UI thread, with resolution between them on a worker. Identity and old-value checks keep
 * a late result from overwriting an edit, removal, or replacement of loaded envelope state.
 */
public final class ReceiptReferenceRepair {
    private ReceiptReferenceRepair() { }

    public static final class Entry {
        public final Transaction transaction;
        public final String reference;
        public final String fileName;
        private Entry(Transaction transaction) {
            this.transaction = transaction;
            reference = transaction.getReceiptImageUri();
            fileName = transaction.getReceiptImageFileName();
        }
        /** Length-prefixed URI avoids collisions and allows one probe per shared association. */
        public String key() { return reference.length() + ":" + reference + ":" + fileName; }
    }

    /** Includes every stored month, independent of current screen filters and receipt age. */
    public static List<Entry> snapshot(List<Envelope> envelopes) {
        List<Entry> entries = new ArrayList<>();
        Set<Transaction> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        if (envelopes == null) return entries;
        for (Envelope envelope : envelopes) {
            if (envelope == null) continue;
            append(envelope.getTransactions(), seen, entries);
            for (Envelope.MonthData month : envelope.getMonthlyDataMap().values()) {
                if (month != null) append(month.transactions, seen, entries);
            }
        }
        return entries;
    }

    private static void append(List<Transaction> transactions, Set<Transaction> seen, List<Entry> entries) {
        if (transactions == null) return;
        for (Transaction transaction : transactions) {
            if (transaction != null && seen.add(transaction) && transaction.getReceiptImageUri() != null
                    && !transaction.getReceiptImageUri().isEmpty()) entries.add(new Entry(transaction));
        }
    }

    /** Returns how many live records changed; the caller persists once only when this is nonzero. */
    public static int apply(List<Envelope> envelopes, List<Entry> entries,
                            Map<String, ReceiptReferenceResolver.Result> results) {
        Set<Transaction> live = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entry entry : snapshot(envelopes)) live.add(entry.transaction);
        int changed = 0;
        for (Entry entry : entries) {
            ReceiptReferenceResolver.Result result = results.get(entry.key());
            Transaction transaction = entry.transaction;
            if (result == null || result.status != ReceiptReferenceResolver.Status.RESOLVED
                    || !live.contains(transaction)
                    || !Objects.equals(entry.reference, transaction.getReceiptImageUri())
                    || !Objects.equals(entry.fileName, transaction.getReceiptImageFileName())) continue;
            if (Objects.equals(result.reference, entry.reference) && Objects.equals(result.fileName, entry.fileName)) continue;
            transaction.setReceiptImageUri(result.reference);
            transaction.setReceiptImageFileName(result.fileName);
            changed++;
        }
        return changed;
    }
}
