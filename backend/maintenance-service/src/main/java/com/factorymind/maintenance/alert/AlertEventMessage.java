package com.factorymind.maintenance.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read from {@value AlertListener#TOPIC}. Same JSON as alert-service's AlertEventMessage; enum values are
 * kept as strings here so a new event type in alert-service does not break this consumer.
 */
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
        BigDecimal warningThreshold,
        BigDecimal criticalThreshold,
        Instant raisedAt,
        Instant occurredAt) {

    /** A confirmed critical condition on an alert nobody has closed yet. */
    public boolean isCriticalAndOpen() {
        return "CRITICAL".equals(severity) && !"RESOLVED".equals(status) && active
                && ("RAISED".equals(eventType) || "ESCALATED".equals(eventType) || "RETRIGGERED".equals(eventType));
    }
}
