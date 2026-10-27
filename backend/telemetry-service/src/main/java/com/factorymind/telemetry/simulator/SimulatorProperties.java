package com.factorymind.telemetry.simulator;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param interval      time between two rounds of readings
 * @param degrading     machines whose readings climb from normal to critical, then drop back as if repaired
 * @param degradeCycle  how long one climb takes
 */
@ConfigurationProperties("telemetry.simulator")
public record SimulatorProperties(boolean enabled, Duration interval, List<UUID> degrading, Duration degradeCycle) {

    public SimulatorProperties {
        interval = interval == null ? Duration.ofSeconds(3) : interval;
        degrading = degrading == null ? List.of() : List.copyOf(degrading);
        degradeCycle = degradeCycle == null ? Duration.ofMinutes(6) : degradeCycle;
    }
}
