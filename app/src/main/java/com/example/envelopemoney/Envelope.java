package com.example.envelopemoney;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Envelope {
    public static class MonthData {
        public double limit;
        public double remaining;
        public List<Transaction> transactions;

        public MonthData(double limit, double remaining) {
            this.limit = limit;
            this.remaining = remaining;
            this.transactions = new ArrayList<>();
        }
    }
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
    @SerializedName("monthlyData")
    private Map<String, MonthData> monthlyData = new HashMap<>();




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

    public MonthData getMonthlyData(String month) {
        if (!monthlyData.containsKey(month)) {
            // Initialize with default values if month doesn't exist
            monthlyData.put(month, new MonthData(limit, remaining));
        }
        return monthlyData.get(month);
    }

    public boolean hasDataForMonth(String month) {
        if (monthlyData == null) return false;
        MonthData data = monthlyData.get(month);
        return data != null && (!data.transactions.isEmpty() || data.remaining != data.limit);
    }
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
        this.limit = originalLimit;
        if (carryOver) {
            // Add remaining to limit for next month
            this.remaining += limit;
        } else {
            // Simple reset without carryover
            this.remaining = originalLimit;
        }

        // Clear transaction history
        this.transactions.clear();
    }
    // Add this method to initialize month data
    public void initializeMonth(String month, boolean carryOver) {
        if (monthlyData == null) monthlyData = new HashMap<>();

        if (!monthlyData.containsKey(month)) {
            // Find previous month data if carrying over
            String previousMonth = getPreviousMonth(month);
            MonthData previousData = monthlyData.get(previousMonth);

            if (carryOver && previousData != null) {
                double newLimit = previousData.limit + previousData.remaining;
                monthlyData.put(month, new MonthData(newLimit, newLimit));
            } else {
                // Use original limit for new months
                monthlyData.put(month, new MonthData(originalLimit, originalLimit));
            }
        }

        // Sync with existing transactions
        MonthData currentData = monthlyData.get(month);
        double spent = 0;
        currentData.transactions.clear(); // Clear any old data
        for (Transaction t : transactions) {
            if (t.getMonth().equals(month)) {
                spent += t.getAmount();
                currentData.transactions.add(t);  // Add the transaction to monthly data
            }
        }
        currentData.remaining = currentData.limit - spent;
    }

    private String getPreviousMonth(String currentMonth) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
            Date date = sdf.parse(currentMonth);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.MONTH, -1);
            return sdf.format(cal.getTime());
        } catch (ParseException e) {
            return null;
        }
    }

    public void migrateLegacyTransactions(String month) {
        if (monthlyData == null) monthlyData = new HashMap<>();

        MonthData data = monthlyData.get(month);
        if (data == null) return;

        // Move transactions to monthly data
        Iterator<Transaction> iterator = transactions.iterator();
        while (iterator.hasNext()) {
            Transaction t = iterator.next();
            if (t.getMonth() == null) {
                data.transactions.add(t);
                iterator.remove();
            }
        }


        // Recalculate remaining
        double total = 0;
        for (Transaction t : data.transactions) {
            total += t.getAmount();
        }
        data.remaining = data.limit - total;
    }

    private MonthData getPreviousMonthData(String currentMonth) {
        // Implement logic to find previous month's data
        return null;
    }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}
