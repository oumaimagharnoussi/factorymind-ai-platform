package com.factorymind.telemetry.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/** A record of {@value SensorCatalogListener#TOPIC}, published by asset-service. Unknown fields are ignored. */
public record SensorDefinitionMessage(
        UUID sensorId,
        UUID assetId,
        SensorType sensorType,
        String unit,
        BigDecimal warningThreshold,
        BigDecimal criticalThreshold) {
}
