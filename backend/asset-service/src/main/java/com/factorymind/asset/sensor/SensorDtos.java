package com.factorymind.asset.sensor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public final class SensorDtos {

    private SensorDtos() {
    }

    public record CreateSensorRequest(
            @NotNull SensorType sensorType,
            @NotBlank @Size(max = 20) String unit,
            @NotNull BigDecimal warningThreshold,
            @NotNull BigDecimal criticalThreshold) {
    }

    public record UpdateSensorRequest(
            @NotBlank @Size(max = 20) String unit,
            @NotNull BigDecimal warningThreshold,
            @NotNull BigDecimal criticalThreshold) {
    }

    public record SensorResponse(
            UUID id, UUID assetId, SensorType sensorType, String unit,
            BigDecimal warningThreshold, BigDecimal criticalThreshold) {
    }
}
