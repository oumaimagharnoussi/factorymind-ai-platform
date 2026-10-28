package com.factorymind.maintenance.workorder;

public enum ActivityType {
    CREATED,
    UPDATED,
    STATUS_CHANGED,
    /** Another alert on the machine was added to this work order. */
    ALERT_LINKED,
    PART_USED,
    PART_RETURNED
}
