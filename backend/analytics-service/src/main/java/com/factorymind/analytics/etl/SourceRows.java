package com.factorymind.analytics.etl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** What the pipeline copies from the service databases, one record per source query. */
public final class SourceRows {

    private SourceRows() {
    }

    public record AssetRow(UUID assetId, String assetCode, String name, String manufacturer, String model,
                           String criticality, LocalDate installedAt, String status, String lineName,
                           String factoryName, String factoryLocation) {
    }

    public record AssetSensorRow(UUID assetId, String sensorType, String unit, BigDecimal warningThreshold,
                                 BigDecimal criticalThreshold) {
    }

    public record WorkOrderRow(UUID workOrderId, String code, UUID assetId, String type, String priority,
                               String status, String assignee, String requestedBy, Instant createdAt,
                               Instant startedAt, Instant completedAt, BigDecimal partsCost) {
    }

    public record WorkOrderAlertRow(UUID alertId, UUID workOrderId) {
    }

    /** An alert that reached CRITICAL, at the time it did. */
    public record CriticalAlertRow(UUID alertId, UUID assetId, String sensorType, Instant failedAt) {
    }

    public record RiskAssessmentRow(UUID assetId, String riskLevel, BigDecimal riskScore, String mainSensorType,
                                    Double hoursToCritical, String modelVersion, Instant assessedAt) {
    }

    public record TelemetryHourRow(UUID assetId, String sensorType, Instant hourStart, String unit, int readings,
                                   BigDecimal avgValue, BigDecimal minValue, BigDecimal maxValue,
                                   int warningReadings, int criticalReadings) {
    }
}
