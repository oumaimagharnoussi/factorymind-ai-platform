package com.factorymind.insights.risk;

import com.factorymind.insights.catalog.SensorCatalog;
import com.factorymind.insights.catalog.SensorDefinitionMessage;
import com.factorymind.insights.common.ServiceUnavailableException;
import com.factorymind.insights.telemetry.ReadingWindows;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every {@code insights.scoring-interval}, sends the reading window of each machine to the model, keeps the
 * answer, and announces the machines whose risk level changed.
 */
@Component
public class RiskScorer {

    /** The model needs this many readings of a sensor to see a trend; it skips sensors with fewer. */
    static final int MIN_READINGS = 10;

    private static final Logger log = LoggerFactory.getLogger(RiskScorer.class);

    private final SensorCatalog catalog;
    private final ReadingWindows windows;
    private final ModelClient model;
    private final RiskAssessmentRepository assessments;
    private final LatestRisks latest;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public RiskScorer(SensorCatalog catalog, ReadingWindows windows, ModelClient model,
                      RiskAssessmentRepository assessments, LatestRisks latest, ApplicationEventPublisher events,
                      TransactionTemplate transaction, Clock clock) {
        this.catalog = catalog;
        this.windows = windows;
        this.model = model;
        this.assessments = assessments;
        this.latest = latest;
        this.events = events;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${insights.scoring-interval:PT10S}", fixedDelayString = "${insights.scoring-interval:PT10S}")
    public void scoreAll() {
        Set<UUID> assetIds = catalog.assetIds();
        latest.all().stream().map(a -> a.prediction().assetId()).filter(id -> !assetIds.contains(id)).toList()
                .forEach(latest::remove);
        for (UUID assetId : assetIds) {
            try {
                score(assetId);
            } catch (ServiceUnavailableException e) {
                // The model is down: no machine can be scored this round, try again at the next one.
                log.warn("Risk scoring skipped: {}", e.getMessage());
                return;
            } catch (RuntimeException e) {
                log.error("Risk scoring failed for machine {}", assetId, e);
            }
        }
    }

    void score(UUID assetId) {
        List<Prediction.SensorInput> sensors = new ArrayList<>();
        for (SensorDefinitionMessage sensor : catalog.sensorsOf(assetId)) {
            List<Prediction.Reading> readings = windows.points(assetId, sensor.sensorType()).stream()
                    .map(p -> new Prediction.Reading(p.recordedAt(), p.value()))
                    .toList();
            if (readings.size() >= MIN_READINGS) {
                sensors.add(new Prediction.SensorInput(sensor.sensorType(), sensor.unit(),
                        sensor.warningThreshold(), sensor.criticalThreshold(), readings));
            }
        }
        if (sensors.isEmpty()) {
            return;
        }
        Prediction prediction = model.predict(new Prediction.Request(assetId, sensors));
        Instant now = clock.instant();
        latest.put(prediction, now);
        transaction.executeWithoutResult(status -> {
            RiskLevel previous = assessments.findFirstByAssetIdOrderByAssessedAtDesc(assetId)
                    .map(RiskAssessment::getRiskLevel)
                    .orElse(null);
            assessments.save(new RiskAssessment(prediction, now));
            if (previous != prediction.riskLevel()) {
                log.info("Machine {} risk {} -> {} ({}, {})", assetId, previous, prediction.riskLevel(),
                        prediction.mainSensorType(), prediction.riskScore());
                Prediction.SensorRisk main = prediction.mainSensor();
                events.publishEvent(new RiskLevelChangedMessage(assetId, previous, prediction.riskLevel(),
                        prediction.riskScore(), prediction.mainSensorType(), main.hoursToCritical(), main.drivers(),
                        prediction.modelVersion(), now));
            }
        });
    }
}
