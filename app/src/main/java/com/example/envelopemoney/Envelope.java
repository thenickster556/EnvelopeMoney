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
    @SerializedName("originalLimit")
    private double originalLimit;
    @SerializedName("remaining")
    private double remaining;
    @SerializedName("transactions")
    private List<Transaction> transactions = new ArrayList<>();
    @SerializedName("selected")
    private boolean isSelected = true;



    public Envelope(String name, double limit) {
        this.name = name;
        this.limit = limit;
        this.originalLimit = limit;
        this.remaining = limit;
    }


    // Getters and setters
    public String getName() { return name; }
    public double getLimit() { return limit; }
    public double getRemaining() { return remaining; }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void setRemaining(double remaining) {
        this.calculateRemaining();
//        this.remaining = remaining;
    }
    public void setName(String name) {
        this.name = name;
    }

    public void setLimit(double limit) {
        this.limit = limit;
        this.originalLimit = limit;
    }

    // Add this method to handle limit changes properly
    public void adjustLimit(double newLimit) {
        double spent = this.limit - this.remaining;
        this.limit = newLimit;
        this.remaining = Math.max(newLimit - spent, 0);
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        calculateRemaining();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void removeTransaction(Transaction transaction) {
        transactions.remove(transaction);
        calculateRemaining();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void updateTransaction(Transaction transaction, double oldAmount) {
        calculateRemaining();
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
        double totalSpent = 0;
        for (Transaction t : transactions) {
            totalSpent += t.getAmount();
        }
        remaining = limit - totalSpent;
    }

    public void reset(boolean carryOver) {
        if (carryOver) {
            // Add remaining to limit for next month
            this.limit += remaining;
            this.remaining = limit;
        } else {
            // Simple reset without carryover
            this.remaining = originalLimit;
            this.limit = originalLimit;
        }

        // Clear transaction history
        this.transactions.clear();
    }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}
