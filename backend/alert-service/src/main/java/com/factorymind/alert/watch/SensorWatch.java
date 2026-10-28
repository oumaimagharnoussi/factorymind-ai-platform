package com.factorymind.alert.watch;

import com.factorymind.alert.telemetry.ReadingStatus;
import com.factorymind.alert.telemetry.SensorStatusChangedMessage;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/** What alert-service knows about one sensor: its last reported level and a debounce timer per alarm level. */
@Entity
@Table(name = "sensor_watches")
public class SensorWatch {

    @EmbeddedId
    private SensorKey key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReadingStatus status;

    @Column(precision = 12, scale = 3)
    private BigDecimal value;

    @Column(length = 20)
    private String unit;

    @Column(name = "warning_threshold", precision = 12, scale = 3)
    private BigDecimal warningThreshold;

    @Column(name = "critical_threshold", precision = 12, scale = 3)
    private BigDecimal criticalThreshold;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "aboveSince", column = @Column(name = "warning_above_since")),
            @AttributeOverride(name = "belowSince", column = @Column(name = "warning_below_since")),
            @AttributeOverride(name = "aboveMillis", column = @Column(name = "warning_above_ms", nullable = false)),
            @AttributeOverride(name = "latched", column = @Column(name = "warning_latched", nullable = false))})
    private LevelTimer warning = new LevelTimer();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "aboveSince", column = @Column(name = "critical_above_since")),
            @AttributeOverride(name = "belowSince", column = @Column(name = "critical_below_since")),
            @AttributeOverride(name = "aboveMillis", column = @Column(name = "critical_above_ms", nullable = false)),
            @AttributeOverride(name = "latched", column = @Column(name = "critical_latched", nullable = false))})
    private LevelTimer critical = new LevelTimer();

    /** The Kafka listener and the debounce timer can touch the same sensor at the same moment. */
    @Version
    private long version;

    protected SensorWatch() {
    }

    public SensorWatch(SensorKey key) {
        this.key = key;
        this.status = ReadingStatus.NORMAL;
        this.changedAt = Instant.EPOCH;
    }

    /**
     * Moves the sensor to its new level. Returns false for a change older than the one already applied
     * (it can only come from a redelivery), which is then ignored.
     */
    public boolean apply(SensorStatusChangedMessage change) {
        if (change.recordedAt().isBefore(changedAt)) {
            return false;
        }
        status = change.status();
        value = change.value();
        unit = change.unit();
        warningThreshold = change.warningThreshold();
        criticalThreshold = change.criticalThreshold();
        changedAt = change.recordedAt();
        warning.record(status.reaches(ReadingStatus.WARNING), changedAt);
        critical.record(status.reaches(ReadingStatus.CRITICAL), changedAt);
        return true;
    }

    public SensorKey getKey() {
        return key;
    }

    public ReadingStatus getStatus() {
        return status;
    }

    public BigDecimal getValue() {
        return value;
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

    public Instant getChangedAt() {
        return changedAt;
    }

    public LevelTimer getWarning() {
        return warning;
    }

    public LevelTimer getCritical() {
        return critical;
    }
}
