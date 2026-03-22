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
import java.util.UUID;

public class Envelope {
    public static class TransferData {
        @SerializedName("id")
        private String id;
        @SerializedName("toEnvelope")
        private String toEnvelope;
        @SerializedName("amount")
        private double amount;

        public TransferData(String toEnvelope, double amount) {
            this(UUID.randomUUID().toString(), toEnvelope, amount);
        }

        public TransferData(String id, String toEnvelope, double amount) {
            this.id = id;
            this.toEnvelope = toEnvelope;
            this.amount = amount;
        }

        public String getId() { return id; }
        public String getToEnvelope() { return toEnvelope; }
        public double getAmount() { return amount; }
        public void setToEnvelope(String toEnvelope) { this.toEnvelope = toEnvelope; }
        public void setAmount(double amount) { this.amount = amount; }
    }

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
    @SerializedName("transfers")
    private List<TransferData> transfers = new ArrayList<>();

    private Double manualRemaining = null;     // if null => no manual override
    private double baselineLimit = 0;         // the limit at the moment of manual override
    private double baselineRemaining = 0;     // the user-set remaining at the time of override


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
    public double getOriginalLimit(){ return originalLimit; }
    public Double getManualRemaining() { return manualRemaining; }
    public Boolean hasBaseline() {
        return Double.isFinite(baselineLimit) && Double.isFinite(baselineRemaining);
    }
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
        this.remaining = remaining;
    }
    public void setManualRemaining(Double remaining) {
        this.manualRemaining = remaining;
    }
    public void setName(String name) {
        this.name = name;
    }

    public void setLimit(double limit) {
        this.limit = limit;
        this.originalLimit = limit;
    }

    public void setOriginalLimit(double originalLimit) {
        this.originalLimit = originalLimit;
    }

    /**
     * User explicitly sets a new manual remaining.
     * This discards any old override, sets new baselines,
     * and uses that override from now on.
     */
    public void setManualOverrideRemaining(double newRemaining) {
        this.manualRemaining   = newRemaining;
        this.baselineRemaining = newRemaining; // seed for future recomputes
        this.remaining         = newRemaining;
    }

    // Adjust limit and remaining based on new limit
    public void adjustLimit(double newLimit, String currentMonth) {
        double oldLimit = this.limit;
        this.limit = newLimit;
        this.originalLimit = newLimit;
        if (manualRemaining != null) {
            // difference from the old baseline limit
            double diff = newLimit - baselineLimit;

            // The new manual remaining is baselineRemaining + diff
            this.manualRemaining = manualRemaining + diff;

            // Update the envelope’s actual remaining to reflect the new manual override
            this.remaining = this.manualRemaining;

            // Update baselines to the new values
            this.baselineLimit = newLimit;
            this.baselineRemaining = this.manualRemaining;
        } else {
           this.calculateRemaining(currentMonth);
        }
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
        if (Objects.equals(t.getMonth(), currentMonth)) {
            initializeMonth(currentMonth, false);

            if (manualRemaining != null) {
                manualRemaining -= t.getAmount();
                remaining = manualRemaining;
            } else {
                calculateRemaining(currentMonth);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void removeTransaction(Transaction t, String currentMonth) {
        transactions.remove(t);

        // If the transaction's month equals currentMonth, update monthlyData as well
        if (Objects.equals(t.getMonth(), currentMonth)) {
            // Refresh current month's data
            initializeMonth(currentMonth, false);

            if (manualRemaining != null) {
                // Add back the amount to the override since we're removing that spending
                manualRemaining += t.getAmount();
                remaining = manualRemaining;
            } else {
                calculateRemaining(currentMonth);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void updateTransaction(Transaction t, double newAmount, String currentMonth) {
        double oldAmount = t.getAmount();
        // We changed the transaction’s spending by (oldAmount - newAmount)
        double diff = newAmount - oldAmount;
        t.setAmount(newAmount);

        if (Objects.equals(t.getMonth(), currentMonth)) {
            initializeMonth(currentMonth, false);
            if (manualRemaining != null) {

                // If oldAmount was 50, newAmount = 70 => diff= -20 => override is decreased by 20
                // If oldAmount was 70, newAmount = 50 => diff= +20 => override is increased by 20
                manualRemaining += diff;
                remaining = manualRemaining;
            } else {
                calculateRemaining(currentMonth);
            }
        }
    }

    public List<Transaction> getTransactions() {
        if (transactions == null) { // Handle deserialization case
            transactions = new ArrayList<>();
        }
        return transactions;
    }

    public List<TransferData> getTransfers() {
        if (transfers == null) {
            transfers = new ArrayList<>();
        }
        return transfers;
    }

    public Map<String, MonthData> getMonthlyDataMap() {
        if (monthlyData == null) {
            monthlyData = new HashMap<>();
        }
        return monthlyData;
    }

    public void addTransfer(String toEnvelope, double amount) {
        getTransfers().add(new TransferData(toEnvelope, amount));
    }

    public void addTransfer(String id, String toEnvelope, double amount) {
        getTransfers().add(new TransferData(id, toEnvelope, amount));
    }

    public void updateTransfer(String id, String toEnvelope, double amount) {
        for (TransferData transfer : getTransfers()) {
            if (Objects.equals(transfer.getId(), id)) {
                transfer.setToEnvelope(toEnvelope);
                transfer.setAmount(amount);
                return;
            }
        }
    }

    public void removeTransfer(String id) {
        getTransfers().removeIf(t -> Objects.equals(t.getId(), id));
    }

    // Recalculate remaining from the global transaction list
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void calculateRemaining(String currentMonth) {
        if (currentMonth == null) return;  // defensive

        double spentThisMonth = 0.0;
        for (Transaction t : transactions) {
            // null-safe month match
            String tm = (t != null) ? t.getMonth() : null;
            if (tm != null && tm.equals(currentMonth)) {
                spentThisMonth += safe(t.getAmount());       // safe() clamps NaN/inf/null to 0
            }
        }

        if (manualRemaining != null) {
            // Baseline must be the starting manual value (seeded during rollover or manual set)
            double baseline = getBaselineRemainingOr(manualRemaining);
            this.remaining = baseline - spentThisMonth;
        } else {
            // Auto path from this month's limit
            this.remaining = safe(limit) - spentThisMonth;
        }
    }

    /**
     * Resets for a new month.
     * If carryOver==true, new limit = base allowance + leftover,
     * and remaining = that new limit.
     * Otherwise both reset to base allowance only.
     */
    public void reset(boolean carryOver) {
        double prevRemaining = this.remaining;        // e.g. $200
        if (carryOver) {
            // new limit should be base allowance + leftover
            this.limit     = this.originalLimit + prevRemaining; // 100 + 200 = 300
            this.remaining = this.limit;                          // start month at full
        } else {
            this.limit     = this.originalLimit;  // back to 100
            this.remaining = this.limit;          // remaining = 100
        }
        this.manualRemaining = null;
    }

    private static double safe(Double v) {
        if (v == null) return 0d;
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0d;
        return v;
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

    /**
     * Repairs nullable collections and non-finite numeric state before month-based logic runs.
     * This is the defensive entry point used during startup and rollover recovery.
     */
    public void sanitizeState(String fallbackMonth) {
        getTransactions();
        getTransfers();
        getMonthlyDataMap();

        if (!Double.isFinite(limit)) {
            limit = 0d;
        }
        if (!Double.isFinite(originalLimit)) {
            originalLimit = limit;
        }
        if (!Double.isFinite(remaining)) {
            remaining = originalLimit;
        }

        if (manualRemaining != null && !Double.isFinite(manualRemaining)) {
            manualRemaining = null;
        }
        if (!Double.isFinite(baselineLimit)) {
            baselineLimit = originalLimit;
        }
        if (!Double.isFinite(baselineRemaining)) {
            baselineRemaining = manualRemaining != null ? manualRemaining : remaining;
        }

        migrateLegacyTransactions(fallbackMonth);
    }

    public void replaceMonthData(String month, double monthLimit) {
        MonthData monthData = new MonthData(safe(monthLimit), safe(monthLimit));
        getMonthlyDataMap().put(month, monthData);
        rebuildMonthData(month);
    }

    public void rebuildMonthData(String month) {
        MonthData monthData = getMonthlyData(month);
        if (monthData.transactions == null) {
            monthData.transactions = new ArrayList<>();
        } else {
            monthData.transactions.clear();
        }
        if (!Double.isFinite(monthData.limit)) {
            monthData.limit = safe(originalLimit);
        }

        double spent = 0d;
        for (Transaction transaction : getTransactions()) {
            if (transaction != null && Objects.equals(transaction.getMonth(), month)) {
                monthData.transactions.add(transaction);
                spent += safe(transaction.getAmount());
            }
        }
        monthData.remaining = monthData.limit - spent;
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
        String[] supportedFormats = new String[]{"yyyy-MM-dd HH:mm", "yyyy-MM-dd"};
        for (String format : supportedFormats) {
            try {
                SimpleDateFormat input = new SimpleDateFormat(format, Locale.getDefault());
                Date date = input.parse(dateStr);
                if (date == null) {
                    continue;
                }
                SimpleDateFormat output = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
                return output.format(date);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }
    private double getBaselineRemainingOr(double fallback) {
        return (Double.isNaN(baselineRemaining)) ? fallback : baselineRemaining;
    }

    public void setBaselineRemaining(double v) { this.baselineRemaining = v; }
    public void setBaselineLimit(double v)     { this.baselineLimit     = v; } // if you use one

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}


