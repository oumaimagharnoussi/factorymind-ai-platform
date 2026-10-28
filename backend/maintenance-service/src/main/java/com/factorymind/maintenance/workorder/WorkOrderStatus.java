package com.factorymind.maintenance.workorder;

import java.util.Set;

/**
 * The life of a work order:
 * <ul>
 *   <li>OPEN: requested, nobody has started;</li>
 *   <li>IN_PROGRESS: a technician is on it (can be put ON_HOLD, e.g. waiting for a part, and resumed);</li>
 *   <li>COMPLETED or CANCELLED: closed for good. Any open state can be cancelled.</li>
 * </ul>
 */
public enum WorkOrderStatus {
    OPEN,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    CANCELLED;

    public Set<WorkOrderStatus> next() {
        return switch (this) {
            case OPEN -> Set.of(IN_PROGRESS, ON_HOLD, CANCELLED);
            case IN_PROGRESS -> Set.of(ON_HOLD, COMPLETED, CANCELLED);
            case ON_HOLD -> Set.of(IN_PROGRESS, CANCELLED);
            case COMPLETED, CANCELLED -> Set.of();
        };
    }

    public boolean canMoveTo(WorkOrderStatus target) {
        return next().contains(target);
    }

    public boolean isClosed() {
        return this == COMPLETED || this == CANCELLED;
    }
}
