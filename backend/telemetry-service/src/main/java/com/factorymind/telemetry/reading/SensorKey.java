package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

/** A sensor is identified by its machine and its type: a machine has at most one sensor of each type. */
@Embeddable
public record SensorKey(
        @Column(name = "asset_id") UUID assetId,
        @Enumerated(EnumType.STRING) @Column(name = "sensor_type", length = 30) SensorType sensorType) {
}
