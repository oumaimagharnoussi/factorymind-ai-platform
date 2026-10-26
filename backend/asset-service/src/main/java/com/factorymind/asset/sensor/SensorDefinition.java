package com.factorymind.asset.sensor;

import com.factorymind.asset.asset.Asset;
import com.factorymind.asset.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A sensor mounted on a machine with its alarm limits. The telemetry and alert services read these
 * limits to decide when a reading is a WARNING or CRITICAL.
 */
@Entity
@Table(name = "sensor_definitions")
public class SensorDefinition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_type", nullable = false, length = 30)
    private SensorType sensorType;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "warning_threshold", nullable = false, precision = 12, scale = 3)
    private BigDecimal warningThreshold;

    @Column(name = "critical_threshold", nullable = false, precision = 12, scale = 3)
    private BigDecimal criticalThreshold;

    protected SensorDefinition() {
    }

    public SensorDefinition(Asset asset, SensorType sensorType, String unit,
                            BigDecimal warningThreshold, BigDecimal criticalThreshold) {
        this.asset = asset;
        this.sensorType = sensorType;
        update(unit, warningThreshold, criticalThreshold);
    }

    public void update(String unit, BigDecimal warningThreshold, BigDecimal criticalThreshold) {
        this.unit = unit;
        this.warningThreshold = warningThreshold;
        this.criticalThreshold = criticalThreshold;
    }

    public Asset getAsset() {
        return asset;
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
