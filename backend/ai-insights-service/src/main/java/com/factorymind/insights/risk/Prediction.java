package com.factorymind.insights.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the Python model ({@code POST /predict}) answers for one machine. The worst sensor sets the machine's risk. */
public record Prediction(
        UUID assetId,
        double riskScore,
        RiskLevel riskLevel,
        double horizonHours,
        String mainSensorType,
        List<SensorRisk> sensors,
        String modelVersion) {

    public SensorRisk mainSensor() {
        return sensors.stream().filter(s -> s.sensorType().equals(mainSensorType)).findFirst().orElseThrow();
    }

    /**
     * @param trendPerHour    change per hour in the sensor's unit (negative when falling)
     * @param hoursToCritical when the trend meets the critical limit; {@code null} when it is not clearly rising
     * @param drivers         plain-language reasons for the score, worst first
     */
    public record SensorRisk(
            String sensorType,
            String unit,
            double currentValue,
            double failureProbability,
            RiskLevel riskLevel,
            double trendPerHour,
            Double hoursToCritical,
            List<String> drivers) {
    }

    /** Body of {@code POST /predict}: the sensor limits and the readings of the current window. */
    public record Request(UUID assetId, List<SensorInput> sensors) {
    }

    public record SensorInput(String sensorType, String unit, BigDecimal warningThreshold,
                              BigDecimal criticalThreshold, List<Reading> readings) {
    }

    public record Reading(Instant recordedAt, double value) {
    }
}
