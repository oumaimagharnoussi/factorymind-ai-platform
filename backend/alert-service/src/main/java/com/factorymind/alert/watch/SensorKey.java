package com.factorymind.alert.watch;

import com.factorymind.alert.telemetry.SensorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

/** One sensor = one machine + one kind of measurement, as in telemetry-service. */
@Embeddable
public record SensorKey(
        @Column(name = "asset_id", nullable = false) UUID assetId,
        @Enumerated(EnumType.STRING) @Column(name = "sensor_type", nullable = false, length = 30) SensorType sensorType) {
}
