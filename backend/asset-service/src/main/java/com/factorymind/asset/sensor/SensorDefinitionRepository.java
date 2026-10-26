package com.factorymind.asset.sensor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SensorDefinitionRepository extends JpaRepository<SensorDefinition, UUID> {

    List<SensorDefinition> findByAssetIdOrderBySensorType(UUID assetId);

    Optional<SensorDefinition> findByIdAndAssetId(UUID id, UUID assetId);

    boolean existsByAssetIdAndSensorType(UUID assetId, SensorType sensorType);

    @Modifying
    @Query("delete from SensorDefinition s where s.asset.id = :assetId")
    void deleteByAssetId(UUID assetId);
}
