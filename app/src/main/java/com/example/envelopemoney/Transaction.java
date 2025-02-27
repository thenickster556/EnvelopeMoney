package com.example.envelopemoney;

import com.google.gson.annotations.SerializedName;

public class Transaction {
    @SerializedName("envelopeName")
    private String envelopeName;
    @SerializedName("amount")
    private double amount;
    @SerializedName("date")
    private String date;
    @SerializedName("comment")
    private String comment;

    public Transaction(String envelopeName, double amount, String date, String comment) {
        this.envelopeName = envelopeName != null ? envelopeName : "Uncategorized";
        this.amount = amount;
        this.date = date != null ? date : "";
        this.comment = comment != null ? comment : "";
    }


    // Add getters
    public String getEnvelopeName() { return envelopeName; }
    public double getAmount() { return amount; }
    public String getDate() { return date; }
    public String getComment() { return comment; }
    public void setAmount(double amount) {  this.amount = amount; }
    public void  setComment(String comment) { this.comment = comment; }
    public void  setDate(String date) { this.date = date; }
    public void  setEnvelopeName(String envelopeName) { this.envelopeName = envelopeName; }

}