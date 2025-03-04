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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLimit(double limit) {
        this.limit = limit;
        this.originalLimit = limit;
    }

    // Adjust limit and remaining based on new limit
    public void adjustLimit(double newLimit) {
        double spent = this.limit - this.remaining;
        this.limit = newLimit;
        this.remaining = Math.max(newLimit - spent, 0);
    }

    private boolean canAfford(double amount) {
        return (remaining >= amount);
    }

    /**
     * Adds a new transaction to the envelope without removing it from the global list.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void addTransaction(Transaction t, String currentMonth) {
        // Ensure the transaction's envelopeName is set correctly
        t.setEnvelopeName(this.name);
        transactions.add(t);
        calculateRemaining();
        // If the transaction's month equals currentMonth, update monthlyData as well
        if (Objects.equals(t.getMonth(), currentMonth)) {
            // Refresh current month's data
            initializeMonth(currentMonth, false);
        }
    }

    /**
     * Removes a transaction from the envelope.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void removeTransaction(Transaction t, String currentMonth) {
        transactions.remove(t);
        calculateRemaining();
        // If the transaction's month equals currentMonth, update monthlyData as well
        if (Objects.equals(t.getMonth(), currentMonth)) {
            // Refresh current month's data
            initializeMonth(currentMonth, false);
        }
    }

    /**
     * Updates an existing transaction.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void updateTransaction(Transaction t, double newAmount) {
        t.setAmount(newAmount);
        calculateRemaining();
    }

    public List<Transaction> getTransactions() {
        if (transactions == null) { // Handle deserialization case
            transactions = new ArrayList<>();
        }
        return transactions;
    }

    // Recalculate remaining from the global transaction list
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void calculateRemaining() {
        double totalSpent = 0;
        for (Transaction t : transactions) {
            totalSpent += t.getAmount();
        }
        remaining = limit - totalSpent;
    }

    /**
     * Resets the envelope for a new month.
     * (Optional: Commented out the clear operation to avoid deleting data.)
     */
    public void reset(boolean carryOver) {
        this.limit = originalLimit;
        if (carryOver) {
            this.remaining += limit;
        } else {
            this.remaining = originalLimit;
        }
        // Commented out: this.transactions.clear();
    }

    /**
     * Initializes monthly data for a given month.
     * It syncs the monthlyData.transactions with the global transactions that have the matching month.
     */
    public void initializeMonth(String month, boolean carryOver) {
        if (monthlyData == null) monthlyData = new HashMap<>();

        if (!monthlyData.containsKey(month)) {
            String previousMonth = getPreviousMonth(month);
            MonthData previousData = monthlyData.get(previousMonth);
            if (carryOver && previousData != null) {
                double newLimit = previousData.limit + previousData.remaining;
                monthlyData.put(month, new MonthData(newLimit, newLimit));
            } else {
                monthlyData.put(month, new MonthData(originalLimit, originalLimit));
            }
        }
        MonthData currentData = monthlyData.get(month);
        currentData.transactions.clear();  // clear previous sync
        double spent = 0;
        for (Transaction t : transactions) {
            // Use Objects.equals to safely compare even if t.getMonth() is null
            if (Objects.equals(t.getMonth(), month)) {
                spent += t.getAmount();
                currentData.transactions.add(t);
            }
        }
        currentData.remaining = currentData.limit - spent;
    }

    private String getPreviousMonth(String currentMonth) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            Date date = sdf.parse(currentMonth);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.MONTH, -1);
            return sdf.format(cal.getTime());
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Migrates legacy transactions by setting their month from the date.
     * Modified so that legacy transactions are not removed from the global list.
     */
    public void migrateLegacyTransactions(String defaultMonthForNull) {
        if (monthlyData == null) monthlyData = new HashMap<>();

        MonthData data = monthlyData.get(defaultMonthForNull);
        if (data == null) {
            data = new MonthData(originalLimit, originalLimit);
            monthlyData.put(defaultMonthForNull, data);
        }

        Iterator<Transaction> iterator = transactions.iterator();
        while (iterator.hasNext()) {
            Transaction t = iterator.next();
            if (t.getMonth() == null) {
                String derivedMonth = parseDateToYearMonth(t.getDate());
                if (derivedMonth == null) {
                    derivedMonth = defaultMonthForNull;
                }
                t.setMonth(derivedMonth);
                MonthData correctData = monthlyData.get(derivedMonth);
                if (correctData == null) {
                    correctData = new MonthData(originalLimit, originalLimit);
                    monthlyData.put(derivedMonth, correctData);
                }
                // Add transaction to the appropriate MonthData without removing it from the global list:
                correctData.transactions.add(t);
                // DO NOT remove t from transactions
                // iterator.remove();
            }
        }

        // Recalculate for the default month
        double total = 0;
        for (Transaction t : data.transactions) {
            total += t.getAmount();
        }
        data.remaining = data.limit - total;
    }

    // Helper to parse date like "2025-03-01 18:07" -> "2025-03"
    private String parseDateToYearMonth(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date date = input.parse(dateStr);
            SimpleDateFormat output = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            return output.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}
