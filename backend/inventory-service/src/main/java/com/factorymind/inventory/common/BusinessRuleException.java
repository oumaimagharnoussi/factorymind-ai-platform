package com.factorymind.inventory.common;

/** A request that breaks a domain rule, e.g. a warning threshold above the critical threshold. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
