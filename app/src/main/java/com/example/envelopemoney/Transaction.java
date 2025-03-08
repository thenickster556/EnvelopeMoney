package com.example.envelopemoney;

import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
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

    public Transaction(String envelopeName, double amount, String date, String comment) {
        this.envelopeName = envelopeName != null ? envelopeName : "Uncategorized";
        this.amount = amount;
        this.date = date != null ? date : "";
        this.comment = comment != null ? comment : "";
        // Set the month based on the provided date, or default to current month if not provided.
        if (this.date.isEmpty()) {
            this.month = MonthTracker.formatMonth(new Date());
        } else {
            this.month = parseDateToMonth(this.date);
        }
    }


    // Add getters
    public String getEnvelopeName() { return envelopeName; }
    public double getAmount() { return amount; }
    public String getDate() { return date; }
    public String getComment() { return comment; }
    public String getMonth() { return month; }
    public void setAmount(double amount) {  this.amount = amount; }
    public void  setComment(String comment) { this.comment = comment; }
    // When setting the date, update the month as well.
    public void setDate(String date) {
        this.date = date;
        if(date != null && !date.isEmpty()){
            String parsedMonth = parseDateToMonth(date);
            if(parsedMonth != null){
                this.month = parsedMonth;
            }
        }
    }
    public void  setEnvelopeName(String envelopeName) { this.envelopeName = envelopeName; }
    public void setMonth(String month) {this.month = month; }
    private String parseDateToMonth(String dateStr) {
        try {
            // Adjust the input format if your date string uses a different format.
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