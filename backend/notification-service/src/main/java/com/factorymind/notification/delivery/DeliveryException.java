package com.factorymind.notification.delivery;

/** A send that failed. The message is stored on the delivery, so it never contains a secret (webhook URL). */
public class DeliveryException extends RuntimeException {

    public DeliveryException(String message) {
        super(message);
    }
}
