package com.philips.onlinestore.exception;

public class InvalidPaymentRequestException extends RuntimeException {

    public InvalidPaymentRequestException(String message) {
        super(message);
    }
}
