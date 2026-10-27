package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One point of a sensor's history, for charts. */
public record ReadingPoint(UUID assetId, SensorType sensorType, BigDecimal value, ReadingStatus status,
                           Instant recordedAt) {

    static ReadingPoint of(SensorReading reading) {
        return new ReadingPoint(reading.getAssetId(), reading.getSensorType(), reading.getValue(),
                reading.getStatus(), reading.getRecordedAt());
    }
}
