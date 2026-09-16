package com.example.envelopemoney;

public final class TransferGroupValidationResult {
    private final boolean valid;
    private final String message;

    private TransferGroupValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public static TransferGroupValidationResult valid() {
        return new TransferGroupValidationResult(true, null);
    }

    public static TransferGroupValidationResult invalid(String message) {
        return new TransferGroupValidationResult(false, message);
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }
}
