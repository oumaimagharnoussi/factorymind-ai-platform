package com.factorymind.insights.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/** A record of {@value SensorCatalogListener#TOPIC}, published by asset-service. Unknown fields are ignored. */
public record SensorDefinitionMessage(
        UUID sensorId,
        UUID assetId,
        String sensorType,
        String unit,
        BigDecimal warningThreshold,
        BigDecimal criticalThreshold) {
}
