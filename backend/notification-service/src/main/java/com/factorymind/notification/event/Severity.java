package com.factorymind.notification.event;

/** How urgent an event is. A routing rule fires for events at or above its minimum severity. */
public enum Severity {
    INFO,
    WARNING,
    CRITICAL;

    public boolean isAtLeast(Severity minimum) {
        return compareTo(minimum) >= 0;
    }
}
