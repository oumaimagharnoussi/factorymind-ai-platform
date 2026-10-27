package com.factorymind.telemetry.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SensorThresholdRepository extends JpaRepository<SensorThreshold, UUID> {

    Optional<SensorThreshold> findByAssetIdAndSensorType(UUID assetId, SensorType sensorType);

    /** Clears a sensor that was replaced by a new one of the same type, in case its tombstone never arrived. */
    @Modifying
    @Query("delete from SensorThreshold t where t.assetId = :assetId and t.sensorType = :type and t.sensorId <> :keep")
    void deleteOthers(UUID assetId, SensorType type, UUID keep);
}
