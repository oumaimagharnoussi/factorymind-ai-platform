package com.factorymind.alert.contract;

import com.factorymind.alert.alert.AlertEventMessage;
import com.factorymind.alert.alert.AlertEventType;
import com.factorymind.alert.alert.AlertSeverity;
import com.factorymind.alert.alert.AlertStatus;
import com.factorymind.alert.telemetry.SensorStatusChangedMessage;
import com.factorymind.alert.telemetry.SensorType;
import com.factorymind.alert.workorder.WorkOrderEventMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Kafka contracts of alert-service ({@code contracts/kafka}): what it publishes matches the shared schemas, and it can
 * read everything the schemas of the topics it consumes allow. A failure here means a change would break another
 * service at runtime; see contracts/README.md for how to change a contract safely.
 */
class KafkaContractTest {

    private static final String ALERTS = "factorymind.alert.alerts";

    @Test
    void publishesAlertEventsAsTheContractSays() {
        for (AlertEventType type : AlertEventType.values()) {
            Contracts.assertProduces(ALERTS, new AlertEventMessage(type, UUID.randomUUID(), UUID.randomUUID(),
                    SensorType.PRESSURE, AlertSeverity.CRITICAL, AlertStatus.OPEN, true, new BigDecimal("214.7"), "bar",
                    new BigDecimal("180"), new BigDecimal("210"), Instant.now().minusSeconds(60), Instant.now()));
        }
        Contracts.assertEnumsDeclared(ALERTS, AlertEventMessage.class);
    }

    @Test
    void readsStatusChangesAndWorkOrders() {
        Contracts.assertConsumes("factorymind.telemetry.sensor-status", SensorStatusChangedMessage.class);
        Contracts.assertConsumes("factorymind.maintenance.work-orders", WorkOrderEventMessage.class);
    }
}
