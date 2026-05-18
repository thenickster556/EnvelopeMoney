package com.example.envelopemoney;

import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persists split purchases: multiple positive expense transactions sharing {@code splitPurchaseGroupId}.
 */
public final class SplitPurchaseSyncHelper {
    private SplitPurchaseSyncHelper() {
    }

    public static boolean isSplitPurchase(@Nullable Transaction t) {
        if (t == null) {
            return false;
        }
        String gid = t.getSplitPurchaseGroupId();
        return gid != null && !gid.isEmpty();
    }

    public static List<Transaction> findTransactionsInGroup(List<Envelope> envelopes, String groupId) {
        List<Transaction> out = new ArrayList<>();
        if (groupId == null || groupId.isEmpty() || envelopes == null) {
            return out;
        }
        for (Envelope e : envelopes) {
            for (Transaction t : e.getTransactions()) {
                if (Objects.equals(groupId, t.getSplitPurchaseGroupId())) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    public static List<SplitPurchaseSliceAllocation> toAllocations(List<Transaction> groupTransactions) {
        List<SplitPurchaseSliceAllocation> slices = new ArrayList<>();
        if (groupTransactions == null) {
            return slices;
        }
        for (Transaction t : groupTransactions) {
            if (t == null || !isSplitPurchase(t)) {
                continue;
            }
            String bid = t.getSplitPurchaseBucketId();
            if (bid == null || bid.isEmpty()) {
                bid = UUID.randomUUID().toString();
            }
            slices.add(new SplitPurchaseSliceAllocation(bid, t.getEnvelopeName(), t.getAmount()));
        }
        return slices;
    }

    /**
     * Collects distinct {@link Transaction#getMonth()} values for rows in the group, then removes all of them.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static Set<String> removeGroup(List<Envelope> envelopes, String groupId) {
        Set<String> months = new HashSet<>();
        if (groupId == null || groupId.isEmpty() || envelopes == null) {
            return months;
        }
        for (Envelope e : envelopes) {
            Iterator<Transaction> it = e.getTransactions().iterator();
            while (it.hasNext()) {
                Transaction t = it.next();
                if (Objects.equals(groupId, t.getSplitPurchaseGroupId())) {
                    String m = t.getMonth();
                    if (m != null) {
                        months.add(m);
                    }
                    it.remove();
                }
            }
        }
        return months;
    }

    /**
     * Replaces the group (if any) and inserts fresh slice rows. Returns calendar months that need
     * {@code synchronizeAllEnvelopesForMonth} after raw list edits.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static Set<String> applyGroup(List<Envelope> envelopes,
                                          @Nullable String existingGroupId,
                                          String date,
                                          String comment,
                                          @Nullable String receiptUri,
                                          List<SplitPurchaseSliceAllocation> slices,
                                          String currentMonth) {
        Set<String> months = new HashSet<>();
        if (envelopes == null || slices == null || slices.isEmpty()) {
            return months;
        }
        String groupId = existingGroupId != null && !existingGroupId.isEmpty()
                ? existingGroupId
                : UUID.randomUUID().toString();
        months.addAll(removeGroup(envelopes, groupId));

        for (SplitPurchaseSliceAllocation slice : slices) {
            if (slice == null) {
                continue;
            }
            String pond = slice.getPondName();
            if (pond == null || pond.trim().isEmpty()) {
                continue;
            }
            Transaction t = new Transaction(pond.trim(), slice.getAmount(), date, comment);
            t.setSplitPurchaseGroupId(groupId);
            t.setSplitPurchaseBucketId(slice.getBucketId());
            if (receiptUri != null && !receiptUri.isEmpty()) {
                t.setReceiptImageUri(receiptUri);
            }
            t.setTransferId(null);
            t.setTransferBucketId(null);
            Envelope env = findEnvelopeByName(envelopes, pond.trim());
            if (env != null) {
                env.addTransaction(t, currentMonth);
                if (t.getMonth() != null) {
                    months.add(t.getMonth());
                }
            }
        }
        return months;
    }

    public static Transaction resolveForEdit(List<Envelope> envelopes, Transaction clicked) {
        if (!isSplitPurchase(clicked)) {
            return clicked;
        }
        List<Transaction> peers = findTransactionsInGroup(envelopes, clicked.getSplitPurchaseGroupId());
        Transaction best = null;
        String bestBid = null;
        for (Transaction t : peers) {
            String bid = t.getSplitPurchaseBucketId();
            if (bid == null) {
                bid = "";
            }
            if (best == null || bid.compareTo(bestBid) < 0) {
                best = t;
                bestBid = bid;
            }
        }
        return best != null ? best : clicked;
    }

    private static Envelope findEnvelopeByName(List<Envelope> envelopes, String envelopeName) {
        if (envelopes == null || envelopeName == null) {
            return null;
        }
        for (Envelope envelope : envelopes) {
            if (Objects.equals(envelope.getName(), envelopeName)) {
                return envelope;
            }
        }
        return null;
    }

    public static double groupTotal(List<Transaction> groupTransactions) {
        return SplitPurchaseGroupDraft.allocatedTotal(toAllocations(groupTransactions));
    }

    public static String formatBreakdownLine(List<Transaction> groupTransactions) {
        List<Transaction> sorted = new ArrayList<>(groupTransactions);
        sorted.sort((a, b) -> {
            String na = a.getEnvelopeName() != null ? a.getEnvelopeName() : "";
            String nb = b.getEnvelopeName() != null ? b.getEnvelopeName() : "";
            return na.compareToIgnoreCase(nb);
        });
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            Transaction t = sorted.get(i);
            sb.append(String.format(Locale.getDefault(), "%s  $%.2f",
                    t.getEnvelopeName() != null ? t.getEnvelopeName() : "?",
                    t.getAmount()));
        }
        return sb.toString();
    }
}
