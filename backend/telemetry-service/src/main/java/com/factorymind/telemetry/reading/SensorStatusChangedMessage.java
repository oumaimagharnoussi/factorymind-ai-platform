package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on {@value TelemetryTopics#SENSOR_STATUS} when a sensor moves between NORMAL, WARNING and CRITICAL.
 * Only changes are sent, not every reading: this is what the alert service will turn into alerts.
 *
 * @param previousStatus {@code null} for the first reading ever seen from this sensor
 */
public record SensorStatusChangedMessage(
        UUID assetId,
        SensorType sensorType,
        ReadingStatus previousStatus,
        ReadingStatus status,
        BigDecimal value,
        String unit,
        BigDecimal warningThreshold,
        BigDecimal criticalThreshold,
        Instant recordedAt) {
}
