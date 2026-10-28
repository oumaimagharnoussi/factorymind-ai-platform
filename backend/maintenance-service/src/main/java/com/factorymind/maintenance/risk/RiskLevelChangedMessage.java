package com.factorymind.maintenance.risk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read from {@value RiskLevelListener#TOPIC}. Same JSON as ai-insights-service's RiskLevelChangedMessage;
 * levels are kept as strings so a new level there does not break this consumer.
 */
public record RiskLevelChangedMessage(
        UUID assetId,
        String previousLevel,
        String riskLevel,
        double riskScore,
        String mainSensorType,
        Double hoursToCritical,
        List<String> drivers,
        String modelVersion,
        Instant assessedAt) {

    public boolean becameHigh() {
        return "HIGH".equals(riskLevel) && !"HIGH".equals(previousLevel);
    }
}
