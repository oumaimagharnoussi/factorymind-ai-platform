package com.factorymind.telemetry.reading;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorStateRepository extends JpaRepository<SensorState, SensorKey> {
}
