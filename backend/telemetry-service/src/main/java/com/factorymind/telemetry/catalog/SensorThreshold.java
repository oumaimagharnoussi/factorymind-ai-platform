package com.factorymind.telemetry.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The alarm limits of one sensor, copied from asset-service. */
@Entity
@Table(name = "sensor_thresholds")
public class SensorThreshold {

    @Id
    @Column(name = "sensor_id")
    private UUID sensorId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_type", nullable = false, length = 30)
    private SensorType sensorType;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "warning_threshold", nullable = false, precision = 12, scale = 3)
    private BigDecimal warningThreshold;

    @Column(name = "critical_threshold", nullable = false, precision = 12, scale = 3)
    private BigDecimal criticalThreshold;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SensorThreshold() {
    }

    public SensorThreshold(UUID sensorId, UUID assetId, SensorType sensorType, String unit,
                           BigDecimal warningThreshold, BigDecimal criticalThreshold) {
        this.sensorId = sensorId;
        this.assetId = assetId;
        this.sensorType = sensorType;
        update(unit, warningThreshold, criticalThreshold);
    }

    public void update(String unit, BigDecimal warningThreshold, BigDecimal criticalThreshold) {
        this.unit = unit;
        this.warningThreshold = warningThreshold;
        this.criticalThreshold = criticalThreshold;
        this.updatedAt = Instant.now();
    }

    public UUID getSensorId() {
        return sensorId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getWarningThreshold() {
        return warningThreshold;
    }

    public BigDecimal getCriticalThreshold() {
        return criticalThreshold;
    }
}
