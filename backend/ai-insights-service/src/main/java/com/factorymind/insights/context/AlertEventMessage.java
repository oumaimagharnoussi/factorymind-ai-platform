package com.factorymind.insights.context;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read from {@value PlantEventsListener#ALERTS_TOPIC}; enum values stay strings so new ones never break this consumer. */
public record AlertEventMessage(
        String eventType,
        UUID alertId,
        UUID assetId,
        String sensorType,
        String severity,
        String status,
        boolean active,
        BigDecimal value,
        String unit,
        Instant raisedAt,
        Instant occurredAt) {
}
