package com.factorymind.maintenance.workorder;

import com.factorymind.maintenance.alert.AlertListener;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** The topic maintenance-service owns, and the dead-letter topic of the one it reads. */
@Configuration(proxyBeanMethods = false)
public class MaintenanceTopics {

    public static final String WORK_ORDERS = "factorymind.maintenance.work-orders";

    private static final int PARTITIONS = 3;

    @Bean
    NewTopic workOrdersTopic() {
        return TopicBuilder.name(WORK_ORDERS).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic alertsDeadLetterTopic() {
        return TopicBuilder.name(AlertListener.TOPIC + "-dlt").partitions(PARTITIONS).replicas(1).build();
    }
}
