package com.factorymind.maintenance.workorder;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends work order changes to Kafka once they are committed. */
@Component
public class WorkOrderPublisher {

    private final KafkaTemplate<Object, Object> kafka;

    public WorkOrderPublisher(KafkaTemplate<Object, Object> kafka) {
        this.kafka = kafka;
    }

    @TransactionalEventListener
    void onWorkOrderEvent(WorkOrderEventMessage event) {
        kafka.send(MaintenanceTopics.WORK_ORDERS, event.assetId().toString(), event);
    }
}
