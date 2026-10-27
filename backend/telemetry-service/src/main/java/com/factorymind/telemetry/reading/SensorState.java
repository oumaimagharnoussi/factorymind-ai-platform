package com.factorymind.telemetry.reading;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** The latest reading of one sensor and since when it has been in its current status. */
@Entity
@Table(name = "sensor_states")
public class SensorState {

    @EmbeddedId
    private SensorKey key;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReadingStatus status;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "status_since", nullable = false)
    private Instant statusSince;

    protected SensorState() {
    }

    public SensorState(SensorKey key, BigDecimal value, ReadingStatus status, Instant recordedAt) {
        this.key = key;
        this.value = value;
        this.status = status;
        this.recordedAt = recordedAt;
        this.statusSince = recordedAt;
    }

    /** Readings can arrive late or out of order; only a newer one moves the state forward. */
    public boolean isOlderThan(Instant time) {
        return recordedAt.isBefore(time);
    }

    public void update(BigDecimal value, ReadingStatus status, Instant recordedAt) {
        if (status != this.status) {
            this.statusSince = recordedAt;
        }
        this.value = value;
        this.status = status;
        this.recordedAt = recordedAt;
    }

    public SensorKey getKey() {
        return key;
    }

    public BigDecimal getValue() {
        return value;
    }

    public ReadingStatus getStatus() {
        return status;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public Instant getStatusSince() {
        return statusSince;
    }
}
