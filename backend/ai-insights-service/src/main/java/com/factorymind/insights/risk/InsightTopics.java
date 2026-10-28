package com.factorymind.insights.risk;

import com.factorymind.insights.catalog.SensorCatalogListener;
import com.factorymind.insights.context.PlantEventsListener;
import com.factorymind.insights.telemetry.ReadingListener;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** The topic ai-insights-service owns, and the dead-letter topics of the ones it reads. */
@Configuration(proxyBeanMethods = false)
public class InsightTopics {

    public static final String RISK_LEVELS = "factorymind.ai.risk-levels";

    private static final int PARTITIONS = 3;

    @Bean
    NewTopic riskLevelsTopic() {
        return TopicBuilder.name(RISK_LEVELS).partitions(PARTITIONS).replicas(1).build();
    }

    /** Same single partition as telemetry-service declares for it. */
    @Bean
    NewTopic sensorDefinitionsDeadLetterTopic() {
        return TopicBuilder.name(SensorCatalogListener.TOPIC + "-dlt").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic readingsDeadLetterTopic() {
        return deadLetter(ReadingListener.TOPIC);
    }

    @Bean
    NewTopic alertsDeadLetterTopic() {
        return deadLetter(PlantEventsListener.ALERTS_TOPIC);
    }

    @Bean
    NewTopic workOrdersDeadLetterTopic() {
        return deadLetter(PlantEventsListener.WORK_ORDERS_TOPIC);
    }

    private static NewTopic deadLetter(String topic) {
        return TopicBuilder.name(topic + "-dlt").partitions(PARTITIONS).replicas(1).build();
    }
}
