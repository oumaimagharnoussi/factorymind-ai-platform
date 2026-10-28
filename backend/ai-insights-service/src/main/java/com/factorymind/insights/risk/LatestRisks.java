package com.factorymind.insights.risk;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** The newest prediction of every machine, with its sensor details, as served to the screens and the assistant. */
@Component
public class LatestRisks {

    private final Map<UUID, AssessedPrediction> latest = new ConcurrentHashMap<>();

    void put(Prediction prediction, Instant assessedAt) {
        latest.put(prediction.assetId(), new AssessedPrediction(prediction, assessedAt));
    }

    void remove(UUID assetId) {
        latest.remove(assetId);
    }

    public Optional<AssessedPrediction> of(UUID assetId) {
        return Optional.ofNullable(latest.get(assetId));
    }

    public Collection<AssessedPrediction> all() {
        return latest.values();
    }

    public record AssessedPrediction(Prediction prediction, Instant assessedAt) {
    }
}
