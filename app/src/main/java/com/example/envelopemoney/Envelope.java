package com.example.envelopemoney;

public class Envelope {
    private String name;
    private double limit;
    private double remaining;

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
}
