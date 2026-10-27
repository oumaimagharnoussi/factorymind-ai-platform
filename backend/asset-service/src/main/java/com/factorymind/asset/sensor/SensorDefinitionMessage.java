package com.factorymind.asset.sensor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What asset-service publishes on {@value SensorCatalogPublisher#TOPIC}: the current alarm limits of one sensor.
 * The record key is the sensor id; a record with a null value (tombstone) means the sensor was removed.
 * This JSON shape is a contract with telemetry-service, so add fields but never rename or remove them.
 */
public record SensorDefinitionMessage(
        UUID sensorId,
        UUID assetId,
        SensorType sensorType,
        String unit,
        BigDecimal warningThreshold,
        BigDecimal criticalThreshold) {

    static SensorDefinitionMessage of(SensorDefinition sensor) {
        return new SensorDefinitionMessage(sensor.getId(), sensor.getAsset().getId(), sensor.getSensorType(),
                sensor.getUnit(), sensor.getWarningThreshold(), sensor.getCriticalThreshold());
    }
}
