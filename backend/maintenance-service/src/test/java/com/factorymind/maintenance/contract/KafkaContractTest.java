package com.factorymind.maintenance.contract;

import com.factorymind.maintenance.alert.AlertEventMessage;
import com.factorymind.maintenance.risk.RiskLevelChangedMessage;
import com.factorymind.maintenance.workorder.ActivityType;
import com.factorymind.maintenance.workorder.WorkOrderEventMessage;
import com.factorymind.maintenance.workorder.WorkOrderPriority;
import com.factorymind.maintenance.workorder.WorkOrderStatus;
import com.factorymind.maintenance.workorder.WorkOrderType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Kafka contracts of maintenance-service ({@code contracts/kafka}): what it publishes matches the shared schemas, and it can
 * read everything the schemas of the topics it consumes allow. A failure here means a change would break another
 * service at runtime; see contracts/README.md for how to change a contract safely.
 */
class KafkaContractTest {

    private static final String WORK_ORDERS = "factorymind.maintenance.work-orders";

    @Test
    void publishesWorkOrderEventsAsTheContractSays() {
        UUID machine = UUID.randomUUID();
        for (ActivityType type : ActivityType.values()) {
            Contracts.assertProduces(WORK_ORDERS, new WorkOrderEventMessage(type, UUID.randomUUID(), "WO-00042", machine,
                    Set.of(UUID.randomUUID()), WorkOrderType.CORRECTIVE, WorkOrderPriority.URGENT, WorkOrderStatus.OPEN,
                    "PRESSURE critical on M-102", "engineer", "alert-service", Instant.now()));
        }
        // A planned work order: no alert, nobody assigned yet.
        Contracts.assertProduces(WORK_ORDERS, new WorkOrderEventMessage(ActivityType.CREATED, UUID.randomUUID(), "WO-00043",
                machine, Set.of(), WorkOrderType.INSPECTION, WorkOrderPriority.HIGH, WorkOrderStatus.OPEN,
                "Inspect M-102: failure risk HIGH", null, "ai-insights", Instant.now()));
        Contracts.assertEnumsDeclared(WORK_ORDERS, WorkOrderEventMessage.class);
    }

    @Test
    void readsAlertsAndRiskLevels() {
        Contracts.assertConsumes("factorymind.alert.alerts", AlertEventMessage.class);
        Contracts.assertConsumes("factorymind.ai.risk-levels", RiskLevelChangedMessage.class);
    }
}
