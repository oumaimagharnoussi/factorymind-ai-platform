package com.factorymind.insights.risk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Model answers for tests. */
public final class Predictions {

    private Predictions() {
    }

    public static Prediction of(UUID assetId, double score, RiskLevel level) {
        return new Prediction(assetId, score, level, 24, "TEMPERATURE", List.of(
                new Prediction.SensorRisk("TEMPERATURE", "°C", 74.2, score, level, 228.0, 0.12,
                        List.of("trend reaches the critical limit in about 7 min", "rising 228.00 °C/h (240.0% of the critical limit per hour)")),
                new Prediction.SensorRisk("VIBRATION", "mm/s", 3.1, 0.12, RiskLevel.LOW, -0.01, null,
                        List.of("stable at 28% of the critical limit"))), "rf-24h-test");
    }

    /** Stores a prediction as the scorer would. */
    public static void store(LatestRisks risks, Prediction prediction, Instant assessedAt) {
        risks.put(prediction, assessedAt);
    }
}
