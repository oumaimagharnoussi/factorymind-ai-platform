package com.factorymind.notification.contract;

import com.factorymind.notification.inbound.AlertEventListener;
import com.factorymind.notification.inbound.RiskLevelListener;
import com.factorymind.notification.inbound.WorkOrderEventListener;
import org.junit.jupiter.api.Test;

/**
 * Kafka contracts of notification-service ({@code contracts/kafka}): what it publishes matches the shared schemas, and it can
 * read everything the schemas of the topics it consumes allow. A failure here means a change would break another
 * service at runtime; see contracts/README.md for how to change a contract safely.
 */
class KafkaContractTest {

    @Test
    void readsAlertsWorkOrdersAndRiskLevels() {
        Contracts.assertConsumes("factorymind.alert.alerts", AlertEventListener.AlertEventMessage.class);
        Contracts.assertConsumes("factorymind.maintenance.work-orders", WorkOrderEventListener.WorkOrderEventMessage.class);
        Contracts.assertConsumes("factorymind.ai.risk-levels", RiskLevelListener.RiskLevelChangedMessage.class);
    }
}
