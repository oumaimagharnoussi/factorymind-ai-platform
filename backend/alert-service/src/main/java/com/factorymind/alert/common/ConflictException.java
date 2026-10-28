package com.factorymind.alert.common;

/** A request that is valid on its own but clashes with the current state (duplicate code, children still attached...). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
