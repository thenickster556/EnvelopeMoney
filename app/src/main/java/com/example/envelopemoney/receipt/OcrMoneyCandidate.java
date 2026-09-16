package com.example.envelopemoney.receipt;

/**
 * One money mention on an OCR line plus the fallback features used for scoring.
 */
public final class OcrMoneyCandidate {

    public final double amount;
    public final boolean dollarSign;
    public final boolean strongTotalLabel;
    public final boolean totalLabel;
    public final boolean bottomHalf;
    public final boolean orderOrPoints;
    public final int lineIndex;
    public final String line;

    public OcrMoneyCandidate(double amount,
                             boolean dollarSign,
                             boolean strongTotalLabel,
                             boolean totalLabel,
                             boolean bottomHalf,
                             boolean orderOrPoints,
                             int lineIndex,
                             String line) {
        this.amount = amount;
        this.dollarSign = dollarSign;
        this.strongTotalLabel = strongTotalLabel;
        this.totalLabel = totalLabel;
        this.bottomHalf = bottomHalf;
        this.orderOrPoints = orderOrPoints;
        this.lineIndex = lineIndex;
        this.line = line != null ? line : "";
    }

    public boolean[] featureFlags() {
        return new boolean[]{
                dollarSign,
                strongTotalLabel,
                totalLabel,
                bottomHalf,
                orderOrPoints
        };
    }
}
