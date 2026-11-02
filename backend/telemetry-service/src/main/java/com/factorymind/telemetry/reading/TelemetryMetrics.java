package com.factorymind.telemetry.reading;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Business metrics of the reading pipeline, scraped by Prometheus from /actuator/prometheus:
 * <ul>
 *   <li>{@code factorymind_telemetry_readings_total{status}}: readings stored, by the level they were judged at;</li>
 *   <li>{@code factorymind_telemetry_readings_duplicate_total}: redeliveries skipped (Kafka is at-least-once);</li>
 *   <li>{@code factorymind_telemetry_status_changes_total{status}}: sensors that moved to a new level.</li>
 * </ul>
 */
@Component
public class TelemetryMetrics {

    private final Map<ReadingStatus, Counter> readings = new EnumMap<>(ReadingStatus.class);
    private final Map<ReadingStatus, Counter> statusChanges = new EnumMap<>(ReadingStatus.class);
    private final Counter duplicates;

    public TelemetryMetrics(MeterRegistry registry) {
        for (ReadingStatus status : ReadingStatus.values()) {
            readings.put(status, Counter.builder("factorymind.telemetry.readings")
                    .description("Sensor readings stored, by the level they were judged at")
                    .tag("status", status.name())
                    .register(registry));
            statusChanges.put(status, Counter.builder("factorymind.telemetry.status.changes")
                    .description("Sensors that moved to a new level")
                    .tag("status", status.name())
                    .register(registry));
        }
        duplicates = Counter.builder("factorymind.telemetry.readings.duplicate")
                .description("Readings delivered again by Kafka and skipped")
                .register(registry);
    }

    void readingStored(ReadingStatus status) {
        readings.get(status).increment();
    }

    void statusChanged(ReadingStatus status) {
        statusChanges.get(status).increment();
    }

    void duplicateSkipped() {
        duplicates.increment();
    }
}
