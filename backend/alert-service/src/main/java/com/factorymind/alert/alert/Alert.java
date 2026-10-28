package com.factorymind.alert.alert;

import com.factorymind.alert.common.BusinessRuleException;
import com.factorymind.alert.telemetry.ReadingStatus;
import com.factorymind.alert.telemetry.SensorType;
import com.factorymind.alert.watch.SensorWatch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One abnormal episode of one sensor, from the moment it was confirmed until a person resolves it.
 * There is at most one unresolved alert per sensor: a sensor that keeps coming back reuses it
 * ({@link #retrigger}) instead of flooding the list.
 */
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_type", nullable = false, length = 30)
    private SensorType sensorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AlertStatus status;

    /** The condition is still there (the debounce has not cleared it). */
    @Column(nullable = false)
    private boolean active;

    /** The sensor's level at its last change, which can be lower than the severity while the alert is open. */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 10)
    private ReadingStatus currentStatus;

    @Column(name = "last_value", precision = 12, scale = 3)
    private BigDecimal lastValue;

    @Column(length = 20)
    private String unit;

    @Column(name = "warning_threshold", precision = 12, scale = 3)
    private BigDecimal warningThreshold;

    @Column(name = "critical_threshold", precision = 12, scale = 3)
    private BigDecimal criticalThreshold;

    /** 1 for a new alert, +1 each time the condition comes back before the alert is resolved. */
    @Column(nullable = false)
    private int occurrences;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(name = "last_raised_at", nullable = false)
    private Instant lastRaisedAt;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "work_order_code", length = 20)
    private String workOrderCode;

    @Column(name = "work_order_status", length = 15)
    private String workOrderStatus;

    @Version
    private long version;

    protected Alert() {
    }

    public static Alert raise(SensorWatch watch, AlertSeverity severity, Instant now) {
        Alert alert = new Alert();
        alert.id = UUID.randomUUID();
        alert.assetId = watch.getKey().assetId();
        alert.sensorType = watch.getKey().sensorType();
        alert.severity = severity;
        alert.status = AlertStatus.OPEN;
        alert.active = true;
        alert.occurrences = 1;
        alert.raisedAt = now;
        alert.lastRaisedAt = now;
        if (severity == AlertSeverity.CRITICAL) {
            alert.escalatedAt = now;
        }
        alert.track(watch);
        return alert;
    }

    /** Keeps the latest level and value of the sensor on the alert, for display. */
    public void track(SensorWatch watch) {
        currentStatus = watch.getStatus();
        lastValue = watch.getValue();
        unit = watch.getUnit();
        warningThreshold = watch.getWarningThreshold();
        criticalThreshold = watch.getCriticalThreshold();
    }

    /** Returns true if the severity went up. */
    public boolean escalate(AlertSeverity to, Instant now) {
        if (to.compareTo(severity) <= 0) {
            return false;
        }
        severity = to;
        escalatedAt = now;
        return true;
    }

    public void retrigger(AlertSeverity to, Instant now) {
        active = true;
        clearedAt = null;
        occurrences++;
        lastRaisedAt = now;
        escalate(to, now);
    }

    public void clear(Instant now) {
        active = false;
        clearedAt = now;
    }

    /** Returns false when someone already acknowledged it. */
    public boolean acknowledge(String by, Instant now) {
        if (status == AlertStatus.RESOLVED) {
            throw new BusinessRuleException("This alert is already resolved");
        }
        if (status == AlertStatus.ACKNOWLEDGED) {
            return false;
        }
        status = AlertStatus.ACKNOWLEDGED;
        acknowledgedAt = now;
        acknowledgedBy = by;
        return true;
    }

    public void resolve(String by, String note, Instant now) {
        if (status == AlertStatus.RESOLVED) {
            throw new BusinessRuleException("This alert is already resolved");
        }
        if (active) {
            throw new BusinessRuleException(
                    "The sensor is still outside its limits. Acknowledge the alert and resolve it once the condition has cleared.");
        }
        if (acknowledgedAt == null) {
            acknowledgedAt = now;
            acknowledgedBy = by;
        }
        status = AlertStatus.RESOLVED;
        resolvedAt = now;
        resolvedBy = by;
        resolutionNote = note;
    }

    /** Returns true if the alert now points to another work order or its status changed. */
    public boolean linkWorkOrder(UUID workOrderId, String code, String workOrderStatus) {
        boolean changed = !Objects.equals(this.workOrderId, workOrderId)
                || !Objects.equals(this.workOrderStatus, workOrderStatus);
        this.workOrderId = workOrderId;
        this.workOrderCode = code;
        this.workOrderStatus = workOrderStatus;
        return changed;
    }

    public boolean isResolved() {
        return status == AlertStatus.RESOLVED;
    }

    public boolean workOrderCompleted() {
        return "COMPLETED".equals(workOrderStatus);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return active;
    }

    public ReadingStatus getCurrentStatus() {
        return currentStatus;
    }

    public BigDecimal getLastValue() {
        return lastValue;
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

    public int getOccurrences() {
        return occurrences;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public Instant getLastRaisedAt() {
        return lastRaisedAt;
    }

    public Instant getEscalatedAt() {
        return escalatedAt;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public String getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public UUID getWorkOrderId() {
        return workOrderId;
    }

    public String getWorkOrderCode() {
        return workOrderCode;
    }

    public String getWorkOrderStatus() {
        return workOrderStatus;
    }

    public long getVersion() {
        return version;
    }
}
