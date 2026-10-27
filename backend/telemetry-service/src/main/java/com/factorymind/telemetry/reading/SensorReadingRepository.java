package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SensorReadingRepository extends JpaRepository<SensorReading, UUID> {

    @Query("""
            select r from SensorReading r
            where r.recordedAt >= :since
              and (:assetId is null or r.assetId = :assetId)
              and (:sensorType is null or r.sensorType = :sensorType)
            order by r.recordedAt desc
            """)
    List<SensorReading> findRecent(UUID assetId, SensorType sensorType, Instant since, Limit limit);

    @Modifying
    @Query("delete from SensorReading r where r.recordedAt < :before")
    int deleteOlderThan(Instant before);
}
