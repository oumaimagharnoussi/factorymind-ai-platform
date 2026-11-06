package com.factorymind.maintenance.workorder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Published on {@value MaintenanceTopics#WORK_ORDERS} after every committed change, keyed by asset id.
 * alert-service uses it to link and close the alerts behind a work order.
 *
 * @param planId the preventive maintenance plan the work order belongs to, null for any other work order
 */
public record WorkOrderEventMessage(
        ActivityType eventType,
        UUID workOrderId,
        String code,
        UUID assetId,
        Set<UUID> alertIds,
        WorkOrderType type,
        WorkOrderPriority priority,
        WorkOrderStatus status,
        String title,
        String assignee,
        String actor,
        UUID planId,
        Instant occurredAt) {

    static WorkOrderEventMessage of(ActivityType eventType, WorkOrder w, String actor, Instant occurredAt) {
        return new WorkOrderEventMessage(eventType, w.getId(), w.getCode(), w.getAssetId(), Set.copyOf(w.getAlertIds()),
                w.getType(), w.getPriority(), w.getStatus(), w.getTitle(), w.getAssignee(), actor, w.getPlanId(), occurredAt);
    }
}
