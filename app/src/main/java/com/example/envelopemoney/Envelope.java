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
}
