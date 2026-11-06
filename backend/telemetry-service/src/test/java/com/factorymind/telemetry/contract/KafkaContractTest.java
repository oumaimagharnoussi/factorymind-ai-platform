package com.factorymind.telemetry.contract;

import com.factorymind.telemetry.catalog.SensorDefinitionMessage;
import com.factorymind.telemetry.catalog.SensorType;
import com.factorymind.telemetry.reading.ReadingStatus;
import com.factorymind.telemetry.reading.SensorReadingMessage;
import com.factorymind.telemetry.reading.SensorStatusChangedMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Kafka contracts of telemetry-service ({@code contracts/kafka}): what it publishes matches the shared schemas, and it can
 * read everything the schemas of the topics it consumes allow. A failure here means a change would break another
 * service at runtime; see contracts/README.md for how to change a contract safely.
 */
class KafkaContractTest {

    private static final String READINGS = "factorymind.telemetry.readings";
    private static final String SENSOR_STATUS = "factorymind.telemetry.sensor-status";
    private static final String SENSOR_DEFINITIONS = "factorymind.asset.sensor-definitions";

    @Test
    void publishesReadingsAsTheContractSays() {
        Contracts.assertProduces(READINGS, new SensorReadingMessage(UUID.randomUUID(), UUID.randomUUID(),
                SensorType.PRESSURE, new BigDecimal("196.4"), Instant.parse("2026-11-06T10:21:03.512Z")));
        Contracts.assertEnumsDeclared(READINGS, SensorReadingMessage.class);
    }

    @Test
    void publishesStatusChangesAsTheContractSays() {
        UUID machine = UUID.randomUUID();
        Contracts.assertProduces(SENSOR_STATUS, new SensorStatusChangedMessage(machine, SensorType.PRESSURE,
                ReadingStatus.WARNING, ReadingStatus.CRITICAL, new BigDecimal("214.7"), "bar", new BigDecimal("180"),
                new BigDecimal("210"), Instant.now()));
        // First reading of a sensor whose definition is not known yet: no previous status, no limits.
        Contracts.assertProduces(SENSOR_STATUS, new SensorStatusChangedMessage(machine, SensorType.VIBRATION,
                null, ReadingStatus.UNKNOWN, new BigDecimal("3.1"), null, null, null, Instant.now()));
        Contracts.assertEnumsDeclared(SENSOR_STATUS, SensorStatusChangedMessage.class);
    }

    @Test
    void readsSensorDefinitionsAndReadings() {
        Contracts.assertConsumes(SENSOR_DEFINITIONS, SensorDefinitionMessage.class);
        Contracts.assertConsumes(READINGS, SensorReadingMessage.class);
    }
}
