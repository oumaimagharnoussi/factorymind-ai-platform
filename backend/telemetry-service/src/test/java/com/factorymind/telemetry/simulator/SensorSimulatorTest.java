package com.factorymind.telemetry.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.factorymind.telemetry.catalog.SensorThreshold;
import com.factorymind.telemetry.catalog.SensorType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class SensorSimulatorTest {

    private final UUID healthy = UUID.randomUUID();
    private final UUID failing = UUID.randomUUID();
    private final Duration cycle = Duration.ofMinutes(6);
    private final SensorSimulator simulator = new SensorSimulator(null, null,
            new SimulatorProperties(true, Duration.ofSeconds(3), List.of(failing), cycle),
            Clock.systemUTC(), RandomGenerator.getDefault());

    @Test
    void healthyMachinesStayBelowTheirWarningLimit() {
        for (int i = 0; i < 500; i++) {
            Instant anyTime = Instant.ofEpochMilli(i * 7_919L);
            assertThat(simulator.valueFor(sensor(healthy), anyTime)).isLessThan(new BigDecimal("75"));
        }
    }

    @Test
    void degradingMachinesStartNormalAndEndAboveTheirCriticalLimit() {
        long offset = Math.floorMod(failing.getLeastSignificantBits() * 0x9E3779B97F4A7C15L, cycle.toMillis());
        Instant cycleStart = Instant.ofEpochMilli(cycle.toMillis() * 1000 - offset);
        Instant cycleEnd = cycleStart.plus(cycle).minusSeconds(3);

        assertThat(simulator.valueFor(sensor(failing), cycleStart)).isLessThan(new BigDecimal("75"));
        assertThat(simulator.valueFor(sensor(failing), cycleEnd)).isGreaterThanOrEqualTo(new BigDecimal("90"));
    }

    private SensorThreshold sensor(UUID assetId) {
        return new SensorThreshold(UUID.randomUUID(), assetId, SensorType.TEMPERATURE, "°C",
                new BigDecimal("75"), new BigDecimal("90"));
    }
}
