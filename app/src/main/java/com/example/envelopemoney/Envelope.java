package com.example.envelopemoney;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class Envelope {
    @SerializedName("name")
    private String name;
    @SerializedName("limit")
    private double limit;
    @SerializedName("remaining")
    private double remaining;
    @SerializedName("transactions")
    private List<Transaction> transactions = new ArrayList<>();



    public Envelope(String name, double limit) {
        this.name = name;
        this.limit = limit;
        this.remaining = limit;
    }

    // Getters and setters
    public String getName() { return name; }
    public double getLimit() { return limit; }
    public double getRemaining() { return remaining; }
    public void setRemaining(double remaining) { this.remaining = remaining; }
    public void setName(String name) {
        this.name = name;
    }

    public void setLimit(double limit) {
        this.limit = limit;
    }

    // Add this method to handle limit changes properly
    public void adjustLimit(double newLimit) {
        double spent = this.limit - this.remaining;
        this.limit = newLimit;
        this.remaining = Math.max(newLimit - spent, 0);
    }
    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        remaining -= transaction.getAmount();
    }
    public List<Transaction> getTransactions() {
        if (transactions == null) { // Handle deserialization case
            transactions = new ArrayList<>();
        }
        return transactions;
    }
    // Update remaining calculation when loading
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void calculateRemaining() {
        double totalSpent = transactions.stream()
                .mapToDouble(Transaction::getAmount)
                .sum();
        remaining = limit - totalSpent;
    }
}
