package com.factorymind.asset.sensor;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Shares the sensor alarm limits with other services through a compacted Kafka topic
 * (event-carried state transfer): telemetry-service keeps its own copy and never queries asset_db.
 *
 * <p>Compaction keeps the latest record per sensor id, so a new consumer reading the topic from the
 * start gets the full catalogue. On startup the whole catalogue is published again: this also covers
 * the demo data inserted by Flyway and any change whose message was lost while Kafka was down.
 */
@Component
public class SensorCatalogPublisher {

    public static final String TOPIC = "factorymind.asset.sensor-definitions";

    private static final Logger log = LoggerFactory.getLogger(SensorCatalogPublisher.class);

    private final KafkaTemplate<Object, Object> kafka;
    private final SensorDefinitionRepository sensors;

    public SensorCatalogPublisher(KafkaTemplate<Object, Object> kafka, SensorDefinitionRepository sensors) {
        this.kafka = kafka;
        this.sensors = sensors;
    }

    @Bean
    NewTopic sensorDefinitionsTopic() {
        return TopicBuilder.name(TOPIC)
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
    }

    @TransactionalEventListener
    void onChange(SensorCatalogChanged change) {
        send(change.sensorId().toString(), change.definition());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishAll() {
        var all = sensors.findAllWithAsset();
        all.forEach(sensor -> send(sensor.getId().toString(), SensorDefinitionMessage.of(sensor)));
        log.info("Published {} sensor definitions to {}", all.size(), TOPIC);
    }

    private void send(String key, SensorDefinitionMessage value) {
        kafka.send(TOPIC, key, value).whenComplete((result, error) -> {
            if (error != null) {
                // The next startup republishes the whole catalogue, which repairs this gap.
                log.warn("Could not publish sensor definition {}: {}", key, error.getMessage());
            }
        });
    }
}
