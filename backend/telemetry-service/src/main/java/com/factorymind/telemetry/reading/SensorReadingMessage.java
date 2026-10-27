package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One measurement on {@value TelemetryTopics#READINGS}, keyed by asset id so all readings of a machine
 * stay in order on one partition. {@code readingId} is chosen by the producer and makes redelivery harmless.
 */
public record SensorReadingMessage(
        UUID readingId,
        UUID assetId,
        SensorType sensorType,
        BigDecimal value,
        Instant recordedAt) {
}
