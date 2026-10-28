package com.factorymind.insights.context;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a local copy of alerts and work orders (a read model), so the reports and the assistant can describe
 * a machine's recent history without calling two other services on every question.
 */
@Component
public class PlantEventsListener {

    public static final String ALERTS_TOPIC = "factorymind.alert.alerts";
    public static final String WORK_ORDERS_TOPIC = "factorymind.maintenance.work-orders";

    private final AlertSnapshotRepository alerts;
    private final WorkOrderSnapshotRepository workOrders;

    public PlantEventsListener(AlertSnapshotRepository alerts, WorkOrderSnapshotRepository workOrders) {
        this.alerts = alerts;
        this.workOrders = workOrders;
    }

    @KafkaListener(id = "insights-alerts", topics = ALERTS_TOPIC, groupId = "ai-insights-service")
    @Transactional
    public void onAlert(AlertEventMessage event) {
        AlertSnapshot snapshot = alerts.findById(event.alertId()).orElseGet(() -> new AlertSnapshot(event.alertId()));
        if (snapshot.apply(event)) {
            alerts.save(snapshot);
        }
    }

    @KafkaListener(id = "insights-work-orders", topics = WORK_ORDERS_TOPIC, groupId = "ai-insights-service")
    @Transactional
    public void onWorkOrder(WorkOrderEventMessage event) {
        WorkOrderSnapshot snapshot = workOrders.findById(event.workOrderId())
                .orElseGet(() -> new WorkOrderSnapshot(event.workOrderId()));
        if (snapshot.apply(event)) {
            workOrders.save(snapshot);
        }
    }
}
