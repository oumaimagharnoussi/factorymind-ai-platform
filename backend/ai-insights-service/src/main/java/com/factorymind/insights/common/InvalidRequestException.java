package com.factorymind.insights.common;

/** The request cannot be served as sent (an unreadable file, an unknown value); nothing was changed. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
