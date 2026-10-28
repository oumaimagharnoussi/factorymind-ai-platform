package com.factorymind.insights.catalog;

import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Fills {@link SensorCatalog} from asset-service's compacted topic; a null value (tombstone) removes a sensor. */
@Component
public class SensorCatalogListener implements ConsumerSeekAware {

    public static final String TOPIC = "factorymind.asset.sensor-definitions";

    private final SensorCatalog catalog;

    public SensorCatalogListener(SensorCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        callback.seekToBeginning(assignments.keySet());
    }

    @KafkaListener(id = "insights-sensor-catalog", topics = TOPIC, groupId = "ai-insights-service-catalog")
    public void onDefinition(@Header(KafkaHeaders.RECEIVED_KEY) String key,
                             @Payload(required = false) SensorDefinitionMessage definition) {
        if (definition == null) {
            catalog.remove(UUID.fromString(key));
        } else {
            catalog.put(definition);
        }
    }
}
