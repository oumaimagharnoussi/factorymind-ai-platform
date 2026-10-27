package com.factorymind.telemetry.catalog;

import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps {@code sensor_thresholds} in step with asset-service. The topic is compacted, so reading it from
 * the beginning on every start rebuilds the full catalogue even if this database was reset.
 */
@Component
public class SensorCatalogListener implements ConsumerSeekAware {

    public static final String TOPIC = "factorymind.asset.sensor-definitions";

    private static final Logger log = LoggerFactory.getLogger(SensorCatalogListener.class);

    private final SensorThresholdRepository thresholds;

    public SensorCatalogListener(SensorThresholdRepository thresholds) {
        this.thresholds = thresholds;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        callback.seekToBeginning(assignments.keySet());
    }

    @KafkaListener(id = "sensor-catalog", topics = TOPIC, groupId = "telemetry-service-catalog")
    @Transactional
    public void onDefinition(@Header(KafkaHeaders.RECEIVED_KEY) String key,
                             @Payload(required = false) SensorDefinitionMessage definition) {
        UUID sensorId = UUID.fromString(key);
        if (definition == null) {
            thresholds.deleteById(sensorId);
            log.debug("Sensor {} removed", sensorId);
            return;
        }
        thresholds.deleteOthers(definition.assetId(), definition.sensorType(), sensorId);
        thresholds.findById(sensorId).ifPresentOrElse(
                existing -> existing.update(definition.unit(), definition.warningThreshold(), definition.criticalThreshold()),
                () -> thresholds.save(new SensorThreshold(sensorId, definition.assetId(), definition.sensorType(),
                        definition.unit(), definition.warningThreshold(), definition.criticalThreshold())));
    }
}
