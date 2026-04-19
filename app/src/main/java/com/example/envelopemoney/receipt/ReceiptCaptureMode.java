package com.example.envelopemoney.receipt;

/**
 * User-selected or auto routing for receipt heuristics (pond is always chosen manually in MM).
 */
public enum ReceiptCaptureMode {
    AUTO,
    RECEIPT,
    RESTAURANT,
    GAS
}
