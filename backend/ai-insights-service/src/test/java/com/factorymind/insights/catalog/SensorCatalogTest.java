package com.factorymind.insights.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SensorCatalogTest {

    private final SensorCatalog catalog = new SensorCatalog();
    private final UUID assetId = UUID.randomUUID();

    @Test
    void aReplacedSensorOfTheSameTypeDoesNotLinger() {
        catalog.put(sensor(UUID.randomUUID(), "TEMPERATURE", "80"));
        UUID replacement = UUID.randomUUID();
        catalog.put(sensor(replacement, "TEMPERATURE", "85"));
        catalog.put(sensor(UUID.randomUUID(), "VIBRATION", "4.5"));

        assertThat(catalog.sensorsOf(assetId)).extracting(SensorDefinitionMessage::sensorType)
                .containsExactly("TEMPERATURE", "VIBRATION");
        assertThat(catalog.find(assetId, "TEMPERATURE")).get()
                .extracting(SensorDefinitionMessage::sensorId).isEqualTo(replacement);
    }

    @Test
    void aTombstoneRemovesTheSensorAndAMachineWithoutSensorsIsNotScored() {
        UUID sensorId = UUID.randomUUID();
        catalog.put(sensor(sensorId, "PRESSURE", "8"));

        catalog.remove(sensorId);

        assertThat(catalog.assetIds()).isEmpty();
    }

    private SensorDefinitionMessage sensor(UUID sensorId, String type, String warning) {
        BigDecimal w = new BigDecimal(warning);
        return new SensorDefinitionMessage(sensorId, assetId, type, "u", w, w.multiply(new BigDecimal("1.2")));
    }
}
