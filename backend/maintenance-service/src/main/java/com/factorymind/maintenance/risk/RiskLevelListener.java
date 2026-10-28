package com.factorymind.maintenance.risk;

import com.factorymind.maintenance.workorder.WorkOrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RiskLevelListener {

    public static final String TOPIC = "factorymind.ai.risk-levels";

    private final WorkOrderService workOrders;

    public RiskLevelListener(WorkOrderService workOrders) {
        this.workOrders = workOrders;
    }

    /** A machine the model now rates HIGH gets an inspection before it fails, not a repair after. */
    @KafkaListener(id = "risk-levels", topics = TOPIC, groupId = "maintenance-service")
    public void onRiskLevelChanged(RiskLevelChangedMessage event) {
        if (event.becameHigh()) {
            workOrders.planInspectionForRisk(event);
        }
    }
}
