package com.factorymind.maintenance.workorder;

public enum WorkOrderPriority {
    LOW,
    MEDIUM,
    HIGH,
    /** Production is or will soon be affected; CRITICAL alerts open work orders at this priority. */
    URGENT
}
