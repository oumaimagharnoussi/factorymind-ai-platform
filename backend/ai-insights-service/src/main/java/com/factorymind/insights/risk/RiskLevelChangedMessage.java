package com.factorymind.insights.risk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published on {@value InsightTopics#RISK_LEVELS}, keyed by asset id, when a machine moves between LOW, MEDIUM
 * and HIGH (not on every scoring). maintenance-service plans an inspection when a machine becomes HIGH.
 *
 * @param previousLevel {@code null} the first time the machine is scored
 * @param drivers       why the main sensor scores this high, in plain language
 */
public record RiskLevelChangedMessage(
        UUID assetId,
        RiskLevel previousLevel,
        RiskLevel riskLevel,
        double riskScore,
        String mainSensorType,
        Double hoursToCritical,
        List<String> drivers,
        String modelVersion,
        Instant assessedAt) {
}
