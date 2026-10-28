package com.factorymind.alert.alert;

import com.factorymind.alert.telemetry.SensorType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlertRepository extends JpaRepository<Alert, UUID>, JpaSpecificationExecutor<Alert> {

    /** At most one per sensor (unique index ux_alerts_unresolved). */
    Optional<Alert> findByAssetIdAndSensorTypeAndStatusNot(UUID assetId, SensorType sensorType, AlertStatus status);

    long countBySeverityAndStatusNot(AlertSeverity severity, AlertStatus status);

    long countByActiveTrueAndStatusNot(AlertStatus status);

    long countByStatus(AlertStatus status);
}
