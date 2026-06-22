package com.exception;

public class PaymentBusinessException extends RuntimeException {
    private final String reason;

    public PaymentBusinessException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}