package com.factorymind.alert.alert;

public enum AlertEventType {
    /** A new alert: the sensor spent long enough above a limit. */
    RAISED,
    /** An unresolved alert went from WARNING to CRITICAL. */
    ESCALATED,
    /** The condition came back after it had cleared, before anyone resolved the alert. */
    RETRIGGERED,
    /** The sensor has been back below the limits long enough. The alert stays until someone resolves it. */
    CLEARED,
    ACKNOWLEDGED,
    RESOLVED,
    /** A work order was opened for the alert, or its work order changed status. */
    WORK_ORDER_UPDATED
}
