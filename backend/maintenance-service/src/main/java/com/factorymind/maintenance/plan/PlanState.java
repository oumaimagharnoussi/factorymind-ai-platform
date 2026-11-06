package com.factorymind.maintenance.plan;

/** Where a maintenance plan stands today, for the screens. */
public enum PlanState {
    /** Switched off: the planner opens nothing. */
    PAUSED,
    /** Its current job is open; the next one waits until this one is closed. */
    JOB_OPEN,
    /** Inside its lead time: the planner opens the work order on its next run. */
    DUE,
    /** Nothing to do before {@code opensOn}. */
    SCHEDULED
}
