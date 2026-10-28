package com.factorymind.maintenance.alert;

import com.factorymind.maintenance.workorder.WorkOrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AlertListener {

    public static final String TOPIC = "factorymind.alert.alerts";

    private final WorkOrderService workOrders;

    public AlertListener(WorkOrderService workOrders) {
        this.workOrders = workOrders;
    }

    /** WARNING alerts are left to an engineer's judgement; CRITICAL ones open an urgent work order. */
    @KafkaListener(id = "alerts", topics = TOPIC, groupId = "maintenance-service")
    public void onAlertEvent(AlertEventMessage event) {
        if (event.isCriticalAndOpen()) {
            workOrders.openForCriticalAlert(event);
        }
    }
}
