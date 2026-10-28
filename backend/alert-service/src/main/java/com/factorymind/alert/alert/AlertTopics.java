package com.factorymind.alert.alert;

import com.factorymind.alert.telemetry.SensorStatusListener;
import com.factorymind.alert.workorder.WorkOrderListener;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** The topic alert-service owns, and the dead-letter topics of the topics it reads. */
@Configuration(proxyBeanMethods = false)
public class AlertTopics {

    public static final String ALERTS = "factorymind.alert.alerts";

    private static final int PARTITIONS = 3;

    @Bean
    NewTopic alertsTopic() {
        return TopicBuilder.name(ALERTS).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic sensorStatusDeadLetterTopic() {
        return TopicBuilder.name(SensorStatusListener.TOPIC + "-dlt").partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic workOrdersDeadLetterTopic() {
        return TopicBuilder.name(WorkOrderListener.TOPIC + "-dlt").partitions(PARTITIONS).replicas(1).build();
    }
}
