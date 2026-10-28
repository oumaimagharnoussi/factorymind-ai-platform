package com.factorymind.maintenance.workorder;

public enum WorkOrderType {
    /** Repair after a fault or an alert. */
    CORRECTIVE,
    /** Planned servicing (oil change, filter, belt) before anything breaks. */
    PREVENTIVE,
    /** Look and measure, no repair planned yet. */
    INSPECTION
}
