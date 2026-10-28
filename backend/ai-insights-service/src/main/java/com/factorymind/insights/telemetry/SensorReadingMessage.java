package com.factorymind.insights.telemetry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A record of {@value ReadingListener#TOPIC}, published by telemetry-service (and its simulator). */
public record SensorReadingMessage(
        UUID readingId,
        UUID assetId,
        String sensorType,
        BigDecimal value,
        Instant recordedAt) {
}
