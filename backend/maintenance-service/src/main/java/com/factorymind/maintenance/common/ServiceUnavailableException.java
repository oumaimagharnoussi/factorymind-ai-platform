package com.factorymind.maintenance.common;

/** Another FactoryMind service this request depends on did not answer. Nothing was changed; try again. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
