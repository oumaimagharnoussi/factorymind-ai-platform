package com.factorymind.alert.workorder;

import com.factorymind.alert.alert.AlertService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WorkOrderListener {

    public static final String TOPIC = "factorymind.maintenance.work-orders";

    private final AlertService alerts;

    public WorkOrderListener(AlertService alerts) {
        this.alerts = alerts;
    }

    @KafkaListener(id = "work-orders", topics = TOPIC, groupId = "alert-service")
    public void onWorkOrderEvent(WorkOrderEventMessage event) {
        alerts.onWorkOrderEvent(event);
    }
}
