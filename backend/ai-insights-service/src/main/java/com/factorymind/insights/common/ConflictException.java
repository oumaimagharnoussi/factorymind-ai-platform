package com.factorymind.insights.common;

/** The request is valid but clashes with the current state (deleting a built-in manual). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
