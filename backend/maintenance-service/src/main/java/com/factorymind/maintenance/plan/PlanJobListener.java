package com.factorymind.maintenance.plan;

import com.factorymind.maintenance.workorder.ActivityType;
import com.factorymind.maintenance.workorder.WorkOrderEventMessage;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tells a plan that its work order was completed or cancelled. It runs inside the transaction that closes the work
 * order, so a FLOATING plan's next date is moved together with the closing, or not at all.
 */
@Component
class PlanJobListener {

    private final PlanService plans;

    PlanJobListener(PlanService plans) {
        this.plans = plans;
    }

    @EventListener
    void onWorkOrderEvent(WorkOrderEventMessage event) {
        if (event.planId() != null && event.eventType() == ActivityType.STATUS_CHANGED && event.status().isClosed()) {
            plans.jobClosed(event.planId(), event.occurredAt());
        }
    }
}
