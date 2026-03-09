package com.example.envelopemoney;

import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Transaction {
    @SerializedName("envelopeName")
    private String envelopeName;
    @SerializedName("amount")
    private double amount;
    @SerializedName("date")
    private String date;
    @SerializedName("comment")
    private String comment;
    @SerializedName("month")
    private String month;
    @SerializedName("transferId")
    private String transferId;
    @SerializedName("recurring")
    private boolean recurring;
    @SerializedName("recurringFrequency")
    private String recurringFrequency;
    @SerializedName("recurringDays")
    private List<Integer> recurringDays;
    @SerializedName("recurringSeriesId")
    private String recurringSeriesId;
    @SerializedName("recurringTemplate")
    private boolean recurringTemplate;

    public Transaction(String envelopeName, double amount, String date, String comment) {
        this.envelopeName = envelopeName != null ? envelopeName : "Uncategorized";
        this.amount = amount;
        this.date = date != null ? date : "";
        this.comment = comment != null ? comment : "";
        this.transferId = null;
        this.recurring = false;
        this.recurringFrequency = null;
        this.recurringDays = new ArrayList<>();
        this.recurringSeriesId = null;
        this.recurringTemplate = false;
        if (this.date.isEmpty()) {
            this.month = MonthTracker.formatMonth(new Date());
        } else {
            this.month = parseDateToMonth(this.date);
        }
    }

    public String getEnvelopeName() { return envelopeName; }
    public double getAmount() { return amount; }
    public String getDate() { return date; }
    public String getComment() { return comment; }
    public String getMonth() { return month; }
    public String getTransferId() { return transferId; }
    public boolean isRecurring() { return recurring; }
    public String getRecurringFrequency() { return recurringFrequency; }
    public List<Integer> getRecurringDays() {
        if (recurringDays == null) {
            recurringDays = new ArrayList<>();
        }
        return recurringDays;
    }
    public String getRecurringSeriesId() { return recurringSeriesId; }
    public boolean isRecurringTemplate() { return recurringTemplate; }

    public void setAmount(double amount) { this.amount = amount; }
    public void setComment(String comment) { this.comment = comment; }
    public void setEnvelopeName(String envelopeName) { this.envelopeName = envelopeName; }
    public void setMonth(String month) { this.month = month; }
    public void setTransferId(String transferId) { this.transferId = transferId; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }
    public void setRecurringFrequency(String recurringFrequency) { this.recurringFrequency = recurringFrequency; }
    public void setRecurringDays(List<Integer> recurringDays) {
        if (recurringDays == null) {
            this.recurringDays = new ArrayList<>();
            return;
        }
        this.recurringDays = new ArrayList<>(recurringDays);
    }
    public void setRecurringSeriesId(String recurringSeriesId) { this.recurringSeriesId = recurringSeriesId; }
    public void setRecurringTemplate(boolean recurringTemplate) { this.recurringTemplate = recurringTemplate; }

    public void setDate(String date) {
        this.date = date;
        if (date != null && !date.isEmpty()) {
            String parsedMonth = parseDateToMonth(date);
            if (parsedMonth != null) {
                this.month = parsedMonth;
            }
        }
    }

    private String parseDateToMonth(String dateStr) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date parsedDate = inputFormat.parse(dateStr);
            SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            return monthFormat.format(parsedDate);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }
}
