package com.factorymind.notification.delivery;

public enum DeliveryStatus {
    /** Waiting to be sent, or to be retried after a failure. */
    PENDING,
    SENT,
    /** Every attempt failed; an admin can retry it from the delivery log. */
    FAILED,
    /** Not sent because the rule's throttle window already had a message about the same thing. */
    SUPPRESSED
}
