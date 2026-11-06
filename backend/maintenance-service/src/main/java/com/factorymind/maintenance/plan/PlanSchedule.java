package com.factorymind.maintenance.plan;

/** How the next due date of a maintenance plan is counted. */
public enum PlanSchedule {
    /**
     * Every N days on the calendar, whenever the last job was done: for inspections required by law or by the
     * manufacturer on fixed dates. A late job does not move the next date.
     */
    FIXED,
    /**
     * N days after the last job was closed: for wear items such as lubrication or filters, where what matters is
     * the time since the last service.
     */
    FLOATING
}
