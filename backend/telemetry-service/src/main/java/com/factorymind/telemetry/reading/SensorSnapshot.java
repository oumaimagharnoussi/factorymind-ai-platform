package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorThreshold;
import com.factorymind.telemetry.catalog.SensorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The live view of one sensor: its latest value with the limits it is judged against (null when unknown). */
public record SensorSnapshot(
        UUID assetId,
        SensorType sensorType,
        BigDecimal value,
        String unit,
        ReadingStatus status,
        Instant recordedAt,
        Instant statusSince,
        BigDecimal warningThreshold,
        BigDecimal criticalThreshold) {

    static SensorSnapshot of(SensorState state, SensorThreshold threshold) {
        return new SensorSnapshot(state.getKey().assetId(), state.getKey().sensorType(), state.getValue(),
                threshold == null ? null : threshold.getUnit(), state.getStatus(), state.getRecordedAt(),
                state.getStatusSince(),
                threshold == null ? null : threshold.getWarningThreshold(),
                threshold == null ? null : threshold.getCriticalThreshold());
    }
}
