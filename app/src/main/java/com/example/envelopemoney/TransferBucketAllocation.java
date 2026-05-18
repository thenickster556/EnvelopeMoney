package com.example.envelopemoney;

import java.util.UUID;

/**
 * Editable transfer allocation bucket for a grouped transfer.
 * One source transaction can reserve money for many destination ponds.
 */
public final class TransferBucketAllocation {
    private final String bucketId;
    private String toEnvelope;
    private double amount;

    public TransferBucketAllocation(String bucketId, String toEnvelope, double amount) {
        this.bucketId = bucketId == null || bucketId.isEmpty() ? UUID.randomUUID().toString() : bucketId;
        this.toEnvelope = toEnvelope;
        this.amount = amount;
    }

    public String getBucketId() {
        return bucketId;
    }

    public String getToEnvelope() {
        return toEnvelope;
    }

    public void setToEnvelope(String toEnvelope) {
        this.toEnvelope = toEnvelope;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
