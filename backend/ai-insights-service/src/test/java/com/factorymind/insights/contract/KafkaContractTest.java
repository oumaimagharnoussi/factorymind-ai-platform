package com.factorymind.insights.contract;

import com.factorymind.insights.catalog.SensorDefinitionMessage;
import com.factorymind.insights.context.AlertEventMessage;
import com.factorymind.insights.context.WorkOrderEventMessage;
import com.factorymind.insights.risk.RiskLevel;
import com.factorymind.insights.risk.RiskLevelChangedMessage;
import com.factorymind.insights.telemetry.SensorReadingMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Kafka contracts of ai-insights-service ({@code contracts/kafka}): what it publishes matches the shared schemas, and it can
 * read everything the schemas of the topics it consumes allow. A failure here means a change would break another
 * service at runtime; see contracts/README.md for how to change a contract safely.
 */
class KafkaContractTest {

    private static final String RISK_LEVELS = "factorymind.ai.risk-levels";

    @Test
    void publishesRiskLevelsAsTheContractSays() {
        Contracts.assertProduces(RISK_LEVELS, new RiskLevelChangedMessage(UUID.randomUUID(), RiskLevel.MEDIUM, RiskLevel.HIGH,
                0.83, "PRESSURE", 1.5, List.of("pressure rising 12.4 bar/h"), "rf-24h-3f9a1c2e", Instant.now()));
        // First assessment of a machine, nothing rising.
        Contracts.assertProduces(RISK_LEVELS, new RiskLevelChangedMessage(UUID.randomUUID(), null, RiskLevel.LOW,
                0.02, null, null, List.of(), "rf-24h-3f9a1c2e", Instant.now()));
        Contracts.assertEnumsDeclared(RISK_LEVELS, RiskLevelChangedMessage.class);
    }

    @Test
    void readsSensorDefinitionsReadingsAlertsAndWorkOrders() {
        Contracts.assertConsumes("factorymind.asset.sensor-definitions", SensorDefinitionMessage.class);
        Contracts.assertConsumes("factorymind.telemetry.readings", SensorReadingMessage.class);
        Contracts.assertConsumes("factorymind.alert.alerts", AlertEventMessage.class);
        Contracts.assertConsumes("factorymind.maintenance.work-orders", WorkOrderEventMessage.class);
    }
}
