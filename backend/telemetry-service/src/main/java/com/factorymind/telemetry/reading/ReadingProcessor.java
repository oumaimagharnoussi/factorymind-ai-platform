package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorThreshold;
import com.factorymind.telemetry.catalog.SensorThresholdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles one reading from Kafka: judge it against the sensor's limits, store it, move the sensor's
 * latest state forward and announce what changed. The announcements ({@link SensorSnapshot} for the
 * live stream, {@link SensorStatusChangedMessage} for Kafka) go out only after the transaction commits.
 */
@Service
public class ReadingProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReadingProcessor.class);

    private final SensorReadingRepository readings;
    private final SensorStateRepository states;
    private final SensorThresholdRepository thresholds;
    private final ApplicationEventPublisher events;

    public ReadingProcessor(SensorReadingRepository readings, SensorStateRepository states,
                            SensorThresholdRepository thresholds, ApplicationEventPublisher events) {
        this.readings = readings;
        this.states = states;
        this.thresholds = thresholds;
        this.events = events;
    }

    @Transactional
    public void process(SensorReadingMessage message) {
        // Kafka delivers at least once: a reading seen before was fully handled already.
        if (readings.existsById(message.readingId())) {
            log.debug("Skipping duplicate reading {}", message.readingId());
            return;
        }
        SensorThreshold threshold = thresholds
                .findByAssetIdAndSensorType(message.assetId(), message.sensorType())
                .orElse(null);
        ReadingStatus status = ThresholdEvaluator.evaluate(message.value(), threshold);
        readings.save(new SensorReading(message, status));

        SensorKey key = new SensorKey(message.assetId(), message.sensorType());
        SensorState state = states.findById(key).orElse(null);
        if (state != null && !state.isOlderThan(message.recordedAt())) {
            // A late reading belongs to the history only; the live state already shows something newer.
            return;
        }
        ReadingStatus previous = state == null ? null : state.getStatus();
        if (state == null) {
            state = states.save(new SensorState(key, message.value(), status, message.recordedAt()));
        } else {
            state.update(message.value(), status, message.recordedAt());
        }

        events.publishEvent(SensorSnapshot.of(state, threshold));
        if (status != previous) {
            events.publishEvent(new SensorStatusChangedMessage(message.assetId(), message.sensorType(), previous,
                    status, message.value(), threshold == null ? null : threshold.getUnit(),
                    threshold == null ? null : threshold.getWarningThreshold(),
                    threshold == null ? null : threshold.getCriticalThreshold(), message.recordedAt()));
        }
    }
}
