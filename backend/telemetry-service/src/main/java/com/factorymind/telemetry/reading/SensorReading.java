package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** The id is assigned by the producer, so {@link Persistable} tells Spring Data to insert without a SELECT first. */
@Entity
@Table(name = "sensor_readings")
public class SensorReading implements Persistable<UUID> {

    @Id
    @Column(name = "reading_id")
    private UUID readingId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_type", nullable = false, length = 30)
    private SensorType sensorType;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReadingStatus status;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Transient
    private boolean isNew = true;

    protected SensorReading() {
    }

    public SensorReading(SensorReadingMessage message, ReadingStatus status) {
        this.readingId = message.readingId();
        this.assetId = message.assetId();
        this.sensorType = message.sensorType();
        this.value = message.value();
        this.status = status;
        this.recordedAt = message.recordedAt();
        this.receivedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return readingId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    void markLoaded() {
        isNew = false;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public SensorType getSensorType() {
        return sensorType;
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
}
