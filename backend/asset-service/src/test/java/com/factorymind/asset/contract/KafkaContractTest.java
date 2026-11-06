package com.factorymind.asset.contract;

import com.factorymind.asset.sensor.SensorDefinitionMessage;
import com.factorymind.asset.sensor.SensorType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Kafka contracts of asset-service ({@code contracts/kafka}): what it publishes matches the shared schemas, and it can
 * read everything the schemas of the topics it consumes allow. A failure here means a change would break another
 * service at runtime; see contracts/README.md for how to change a contract safely.
 */
class KafkaContractTest {

    private static final String SENSOR_DEFINITIONS = "factorymind.asset.sensor-definitions";

    @Test
    void publishesSensorDefinitionsAsTheContractSays() {
        for (SensorType type : SensorType.values()) {
            Contracts.assertProduces(SENSOR_DEFINITIONS, new SensorDefinitionMessage(UUID.randomUUID(), UUID.randomUUID(),
                    type, "bar", new BigDecimal("180.000"), new BigDecimal("210.000")));
        }
        Contracts.assertEnumsDeclared(SENSOR_DEFINITIONS, SensorDefinitionMessage.class);
    }
}
