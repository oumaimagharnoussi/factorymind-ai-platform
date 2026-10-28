package com.factorymind.insights.context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The latest known state of an alert, copied from alert-service's events for the assistant's context. */
@Entity
@Table(name = "alert_snapshots")
public class AlertSnapshot {

    @Id
    private UUID alertId;

    @Column(nullable = false)
    private UUID assetId;

    @Column(nullable = false, length = 30)
    private String sensorType;

    @Column(nullable = false, length = 10)
    private String severity;

    @Column(nullable = false, length = 15)
    private String status;

    private boolean active;

    @Column(name = "last_value", precision = 12, scale = 3)
    private BigDecimal value;

    @Column(length = 20)
    private String unit;

    @Column(nullable = false)
    private Instant raisedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AlertSnapshot() {
    }

    AlertSnapshot(UUID alertId) {
        this.alertId = alertId;
    }

    /** Events of one alert come in order (same partition), but a replay must not roll a newer state back. */
    boolean apply(AlertEventMessage event) {
        if (updatedAt != null && event.occurredAt().isBefore(updatedAt)) {
            return false;
        }
        assetId = event.assetId();
        sensorType = event.sensorType();
        severity = event.severity();
        status = event.status();
        active = event.active();
        value = event.value();
        unit = event.unit();
        raisedAt = event.raisedAt();
        updatedAt = event.occurredAt();
        return true;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getSensorType() {
        return sensorType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return active;
    }

    public BigDecimal getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
