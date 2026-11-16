package com.philips.onlinestore.exception;

public class PaymentConflictException extends RuntimeException {

    public PaymentConflictException(String message) {
        super(message);
    }
}
