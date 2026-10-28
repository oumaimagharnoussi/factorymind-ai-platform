package com.factorymind.alert.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read from {@value SensorStatusListener#TOPIC}: telemetry-service publishes one when a sensor moves between
 * NORMAL, WARNING and CRITICAL. Same JSON as telemetry-service's class of the same name.
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
