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
        public final String transactionDate;
        private Entry(Transaction transaction) {
            this.transaction = transaction;
            reference = transaction.getReceiptImageUri();
            fileName = transaction.getReceiptImageFileName();
            transactionDate = transaction.getDate();
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

    /**
     * One-pass repair: working stored URIs win, then unique filenames, then a leftover file whose
     * capture day uniquely matches one unmatched receipt. Date matching is skipped when album
     * access is restricted so a partial owned-only list cannot steal another day's picture.
     */
    public static Map<String, ReceiptReferenceResolver.Result> resolveAll(
            ReceiptReferenceResolver.Source source, List<Entry> entries, TimeZone zone) {
        Map<String, ReceiptReferenceResolver.Result> results = new LinkedHashMap<>();
        if (source == null || entries == null || entries.isEmpty()) return results;
        TimeZone tz = zone != null ? zone : TimeZone.getDefault();
        Set<String> reserved = new HashSet<>();
        List<ReceiptAlbumMatcher.Claim> leftover = new ArrayList<>();
        Map<String, ReceiptReferenceResolver.Result> originals = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (Entry entry : entries) {
            if (entry == null || !seen.add(entry.key())) continue;
            ReceiptReferenceResolver.Result original = source.inspect(entry.reference);
            originals.put(entry.key(), original);
            String expectedName = ReceiptReferenceResolver.validFileName(entry.fileName)
                    ? entry.fileName
                    : ReceiptReferenceResolver.fileNameFromReference(entry.reference);
            if (expectedName == null && original != null
                    && ReceiptReferenceResolver.validFileName(original.fileName)) {
                expectedName = original.fileName;
            }
            if (original != null && original.status == ReceiptReferenceResolver.Status.RESOLVED
                    && (expectedName == null || expectedName.equals(original.fileName))) {
                results.put(entry.key(), original);
                if (original.reference != null) reserved.add(original.reference);
                continue;
            }
            leftover.add(new ReceiptAlbumMatcher.Claim(entry.key(), expectedName, entry.transactionDate));
        }
        if (leftover.isEmpty()) return results;
        List<ReceiptReferenceResolver.Result> album;
        try {
            album = source.readAlbum();
        } catch (SecurityException denied) {
            for (ReceiptAlbumMatcher.Claim claim : leftover) {
                results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                        ReceiptReferenceResolver.Status.PERMISSION_REQUIRED, claim.fileName));
            }
            return results;
        }
        boolean allowDate = !source.albumAccessRestricted();
        Map<String, ReceiptReferenceResolver.Result> matched =
                ReceiptAlbumMatcher.assign(leftover, album, tz, reserved, allowDate);
        for (ReceiptAlbumMatcher.Claim claim : leftover) {
            ReceiptReferenceResolver.Result match = matched.get(claim.key);
            if (match != null && match.status == ReceiptReferenceResolver.Status.RESOLVED
                    && match.reference != null) {
                ReceiptReferenceResolver.Result verified = source.inspect(match.reference);
                if (verified.status == ReceiptReferenceResolver.Status.RESOLVED) {
                    if (claim.fileName != null && verified.fileName != null
                            && !claim.fileName.equals(verified.fileName)) {
                        results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                                ReceiptReferenceResolver.Status.MISSING, claim.fileName));
                        continue;
                    }
                    String name = verified.fileName != null ? verified.fileName : match.fileName;
                    results.put(claim.key, ReceiptReferenceResolver.Result.resolved(
                            verified.reference, name, match.captureTimeMs));
                    continue;
                }
                results.put(claim.key, verified);
                continue;
            }
            if (source.albumAccessRestricted()) {
                ReceiptReferenceResolver.Result original = originals.get(claim.key);
                if (original != null
                        && original.status == ReceiptReferenceResolver.Status.PERMISSION_REQUIRED) {
                    results.put(claim.key, original);
                } else {
                    results.put(claim.key, ReceiptReferenceResolver.Result.failure(
                            ReceiptReferenceResolver.Status.PERMISSION_REQUIRED, claim.fileName));
                }
                continue;
            }
            if (match != null) {
                results.put(claim.key, match);
            } else {
                ReceiptReferenceResolver.Result original = originals.get(claim.key);
                results.put(claim.key, original != null ? original
                        : ReceiptReferenceResolver.Result.failure(ReceiptReferenceResolver.Status.MISSING));
            }
        }
        return results;
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
