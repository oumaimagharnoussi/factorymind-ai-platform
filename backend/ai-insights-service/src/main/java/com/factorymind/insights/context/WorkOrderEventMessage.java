package com.factorymind.insights.context;

import java.time.Instant;
import java.util.UUID;

/** Read from {@value PlantEventsListener#WORK_ORDERS_TOPIC}. */
public record WorkOrderEventMessage(
        String eventType,
        UUID workOrderId,
        String code,
        UUID assetId,
        String type,
        String priority,
        String status,
        String title,
        String assignee,
        Instant occurredAt) {
}
