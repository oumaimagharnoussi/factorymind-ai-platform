package com.factorymind.alert.alert;

import com.factorymind.alert.alert.AlertDtos.AlertEvent;
import com.factorymind.alert.telemetry.SensorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on {@value AlertTopics#ALERTS}, keyed by asset id. maintenance-service opens a work order
 * for CRITICAL alerts; the AI and notification services of later phases will read the same events.
 */
public record AlertEventMessage(
        AlertEventType eventType,
        UUID alertId,
        UUID assetId,
        SensorType sensorType,
        AlertSeverity severity,
        AlertStatus status,
        boolean active,
        BigDecimal value,
        String unit,
        BigDecimal warningThreshold,
        BigDecimal criticalThreshold,
        Instant raisedAt,
        Instant occurredAt) {

    static AlertEventMessage of(AlertEvent event, Instant occurredAt) {
        var a = event.alert();
        return new AlertEventMessage(event.type(), a.id(), a.assetId(), a.sensorType(), a.severity(), a.status(),
                a.active(), a.lastValue(), a.unit(), a.warningThreshold(), a.criticalThreshold(), a.raisedAt(),
                occurredAt);
    }
}
