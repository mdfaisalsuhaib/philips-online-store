package com.philips.onlinestore.exception;

public class InvalidCartQuantityException extends RuntimeException {

    public InvalidCartQuantityException(String message) {
        super(message);
    }
}
