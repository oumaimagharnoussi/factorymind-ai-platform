package com.factorymind.asset.sensor;

import java.util.UUID;

/**
 * Raised inside a transaction when a sensor is created, edited or removed. It is sent to Kafka only once
 * that transaction commits, so other services never hear about a change that was rolled back.
 *
 * @param definition the new limits, or {@code null} when the sensor was removed
 */
public record SensorCatalogChanged(UUID sensorId, SensorDefinitionMessage definition) {

    static SensorCatalogChanged saved(SensorDefinition sensor) {
        return new SensorCatalogChanged(sensor.getId(), SensorDefinitionMessage.of(sensor));
    }

    public static SensorCatalogChanged removed(UUID sensorId) {
        return new SensorCatalogChanged(sensorId, null);
    }
}
