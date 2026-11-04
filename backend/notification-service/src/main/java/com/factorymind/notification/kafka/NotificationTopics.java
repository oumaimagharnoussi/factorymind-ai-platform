package com.factorymind.notification.kafka;

import com.factorymind.notification.inbound.AlertEventListener;
import com.factorymind.notification.inbound.RiskLevelListener;
import com.factorymind.notification.inbound.WorkOrderEventListener;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * notification-service owns no topic: it only reads. These are the dead-letter topics of what it reads
 * (shared with the other readers of the same topics; creating one that exists does nothing).
 */
@Configuration(proxyBeanMethods = false)
public class NotificationTopics {

    private static final int PARTITIONS = 3;

    @Bean
    NewTopic alertsDeadLetterTopic() {
        return TopicBuilder.name(AlertEventListener.TOPIC + "-dlt").partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic workOrdersDeadLetterTopic() {
        return TopicBuilder.name(WorkOrderEventListener.TOPIC + "-dlt").partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic riskLevelsDeadLetterTopic() {
        return TopicBuilder.name(RiskLevelListener.TOPIC + "-dlt").partitions(PARTITIONS).replicas(1).build();
    }
}
