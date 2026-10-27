package com.factorymind.telemetry.reading;

import com.factorymind.telemetry.catalog.SensorCatalogListener;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Topics owned by telemetry-service, created on startup if they do not exist. */
@Configuration(proxyBeanMethods = false)
public class TelemetryTopics {

    public static final String READINGS = "factorymind.telemetry.readings";
    public static final String SENSOR_STATUS = "factorymind.telemetry.sensor-status";

    /** Three partitions let up to three telemetry-service instances share the load. */
    private static final int PARTITIONS = 3;

    @Bean
    NewTopic readingsTopic() {
        return TopicBuilder.name(READINGS).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic readingsDeadLetterTopic() {
        return TopicBuilder.name(READINGS + "-dlt").partitions(PARTITIONS).replicas(1).build();
    }

    /** Catalogue records this service could not read; the catalogue topic itself belongs to asset-service. */
    @Bean
    NewTopic catalogDeadLetterTopic() {
        return TopicBuilder.name(SensorCatalogListener.TOPIC + "-dlt").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic sensorStatusTopic() {
        return TopicBuilder.name(SENSOR_STATUS).partitions(PARTITIONS).replicas(1).build();
    }
}
