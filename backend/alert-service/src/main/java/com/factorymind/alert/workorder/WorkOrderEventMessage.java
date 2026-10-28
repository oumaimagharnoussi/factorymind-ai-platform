package com.factorymind.alert.workorder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read from {@value WorkOrderListener#TOPIC}. Only the fields alert-service needs; the JSON from
 * maintenance-service carries more, which is ignored.
 *
 * @param alertIds the alerts this work order answers (empty for a work order that was not opened for an alert)
 * @param status  the work order's status after the event (OPEN, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED)
 * @param actor   user name of whoever made the change
 */
public record WorkOrderEventMessage(
        String eventType,
        UUID workOrderId,
        String code,
        UUID assetId,
        List<UUID> alertIds,
        String status,
        String actor,
        Instant occurredAt) {
}
