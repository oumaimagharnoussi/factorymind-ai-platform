package com.factorymind.alert.alert;

/** What people have done about an alert. Whether the condition is still there is {@code Alert.active}. */
public enum AlertStatus {
    /** Nobody has looked at it yet. */
    OPEN,
    /** Someone owns it (by hand, or because a work order was opened for it). */
    ACKNOWLEDGED,
    /** Closed. A new excursion of the same sensor opens a new alert. */
    RESOLVED
}
