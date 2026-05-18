package com.example.envelopemoney;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Centralizes grouped transfer persistence and mirror transaction synchronization.
 * The source transaction keeps the full spend amount; mirror rows represent only reserved transfer buckets.
 */
public final class TransferSyncHelper {
    private TransferSyncHelper() {
    }

    public static Transaction resolveAnchorTransaction(List<Envelope> envelopes, Transaction transaction) {
        if (transaction == null) {
            return null;
        }
        String transferId = transaction.getTransferId();
        if (transferId == null || transferId.isEmpty()) {
            return transaction;
        }
        Envelope owner = findTransferOwner(envelopes, transferId);
        if (owner == null) {
            return transaction;
        }
        for (Transaction candidate : owner.getTransactions()) {
            if (Objects.equals(candidate.getTransferId(), transferId)
                    && (candidate.getTransferBucketId() == null || candidate.getTransferBucketId().isEmpty())) {
                return candidate;
            }
        }
        return transaction;
    }

    public static Envelope findTransferOwner(List<Envelope> envelopes, String transferId) {
        if (transferId == null || transferId.isEmpty() || envelopes == null) {
            return null;
        }
        for (Envelope envelope : envelopes) {
            for (Envelope.TransferData transfer : envelope.getTransfers()) {
                if (Objects.equals(transfer.getId(), transferId)) {
                    return envelope;
                }
            }
        }
        return null;
    }

    public static List<TransferBucketAllocation> getAllocations(List<Envelope> envelopes, String transferId) {
        List<TransferBucketAllocation> allocations = new ArrayList<>();
        if (transferId == null || transferId.isEmpty()) {
            return allocations;
        }
        Envelope owner = findTransferOwner(envelopes, transferId);
        if (owner == null) {
            return allocations;
        }
        int legacyIndex = 0;
        for (Envelope.TransferData transfer : owner.getTransfers()) {
            if (!Objects.equals(transfer.getId(), transferId)) {
                continue;
            }
            String bucketId = transfer.getBucketId();
            if (bucketId == null || bucketId.isEmpty()) {
                bucketId = "legacy-" + transferId + "-" + legacyIndex++;
            }
            allocations.add(new TransferBucketAllocation(bucketId, transfer.getToEnvelope(), transfer.getAmount()));
        }
        return allocations;
    }

    public static Envelope.TransferData findTransferByBucket(List<Envelope> envelopes, String transferId, String bucketId) {
        if (transferId == null || transferId.isEmpty() || bucketId == null || bucketId.isEmpty()) {
            return null;
        }
        for (Envelope envelope : envelopes) {
            for (Envelope.TransferData transfer : envelope.getTransfers()) {
                if (!Objects.equals(transfer.getId(), transferId)) {
                    continue;
                }
                if (Objects.equals(normalizeBucketId(transfer), bucketId)) {
                    return transfer;
                }
            }
        }
        return null;
    }

    public static double allocatedTotal(List<Envelope> envelopes, String transferId) {
        return TransferGroupDraft.allocatedTotal(getAllocations(envelopes, transferId));
    }

    public static void detachTransferGroup(List<Envelope> envelopes, Transaction sourceTransaction) {
        if (sourceTransaction == null) {
            return;
        }
        String transferId = sourceTransaction.getTransferId();
        if (transferId == null || transferId.isEmpty()) {
            sourceTransaction.setTransferBucketId(null);
            return;
        }
        removeTransferGroupData(envelopes, transferId);
        removeMirrorTransactions(envelopes, transferId, sourceTransaction);
        sourceTransaction.setTransferId(null);
        sourceTransaction.setTransferBucketId(null);
    }

    public static void applyTransferGroup(List<Envelope> envelopes,
                                          Transaction sourceTransaction,
                                          String sourceEnvelopeName,
                                          List<TransferBucketAllocation> allocations) {
        if (sourceTransaction == null) {
            return;
        }

        String transferId = sourceTransaction.getTransferId();
        if (transferId == null || transferId.isEmpty()) {
            transferId = UUID.randomUUID().toString();
            sourceTransaction.setTransferId(transferId);
        }
        sourceTransaction.setTransferBucketId(null);
        removeTransferGroupData(envelopes, transferId);
        removeMirrorTransactions(envelopes, transferId, sourceTransaction);

        Envelope owner = findEnvelopeByName(envelopes, sourceEnvelopeName);
        if (owner == null) {
            return;
        }

        for (TransferBucketAllocation allocation : allocations) {
            if (allocation == null || allocation.getToEnvelope() == null || allocation.getToEnvelope().trim().isEmpty()) {
                continue;
            }
            owner.addTransfer(transferId,
                    allocation.getBucketId(),
                    allocation.getToEnvelope(),
                    Math.abs(allocation.getAmount()));

            Envelope destination = findEnvelopeByName(envelopes, allocation.getToEnvelope());
            if (destination == null) {
                continue;
            }
            String sourceComment = sourceTransaction.getComment();
            String mirrorComment = (sourceComment == null || sourceComment.isEmpty())
                    ? "Transfer from " + sourceEnvelopeName
                    : "Transfer from " + sourceEnvelopeName + " | " + sourceComment;
            Transaction mirror = new Transaction(
                    allocation.getToEnvelope(),
                    -Math.abs(allocation.getAmount()),
                    sourceTransaction.getDate(),
                    mirrorComment);
            mirror.setTransferId(transferId);
            mirror.setTransferBucketId(allocation.getBucketId());
            mirror.setReceiptImageUri(sourceTransaction.getReceiptImageUri());
            destination.getTransactions().add(mirror);
        }
    }

    private static void removeTransferGroupData(List<Envelope> envelopes, String transferId) {
        for (Envelope envelope : envelopes) {
            envelope.getTransfers().removeIf(transfer -> Objects.equals(transfer.getId(), transferId));
        }
    }

    private static void removeMirrorTransactions(List<Envelope> envelopes, String transferId, Transaction sourceTransaction) {
        for (Envelope envelope : envelopes) {
            Iterator<Transaction> iterator = envelope.getTransactions().iterator();
            while (iterator.hasNext()) {
                Transaction candidate = iterator.next();
                if (!Objects.equals(candidate.getTransferId(), transferId)) {
                    continue;
                }
                if (candidate == sourceTransaction) {
                    continue;
                }
                iterator.remove();
            }
        }
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

    private static String normalizeBucketId(Envelope.TransferData transfer) {
        String bucketId = transfer.getBucketId();
        if (bucketId == null || bucketId.isEmpty()) {
            return String.format(Locale.US, "legacy-%s-%s",
                    transfer.getId(),
                    transfer.getToEnvelope() == null ? "unknown" : transfer.getToEnvelope());
        }
        return bucketId;
    }
}
