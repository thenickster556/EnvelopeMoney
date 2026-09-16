package com.example.envelopemoney;

import java.util.UUID;

/**
 * One pond slice for a split purchase (positive expense in that pond).
 */
public final class SplitPurchaseSliceAllocation {
    private final String bucketId;
    private String pondName;
    private double amount;

    public SplitPurchaseSliceAllocation(String bucketId, String pondName, double amount) {
        this.bucketId = bucketId == null || bucketId.isEmpty() ? UUID.randomUUID().toString() : bucketId;
        this.pondName = pondName;
        this.amount = amount;
    }

    public String getBucketId() {
        return bucketId;
    }

    public String getPondName() {
        return pondName;
    }

    public void setPondName(String pondName) {
        this.pondName = pondName;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
