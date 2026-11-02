package com.factorymind.analytics.etl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Warehouse loads for Prometheus (/actuator/prometheus):
 * <ul>
 *   <li>{@code factorymind_analytics_etl_seconds{status}}: how long each load took, SUCCEEDED / FAILED / SKIPPED;</li>
 *   <li>{@code factorymind_analytics_etl_last_success_seconds}: when the last load succeeded (epoch seconds),
 *       so an alert fires when Power BI starts showing stale data.</li>
 * </ul>
 */
@Component
public class EtlMetrics {

    private final MeterRegistry registry;
    private final AtomicLong lastSuccess = new AtomicLong();

    public EtlMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("factorymind.analytics.etl.last.success.seconds", lastSuccess);
    }

    void recordRun(String status, Instant startedAt, Instant finishedAt) {
        Timer.builder("factorymind.analytics.etl")
                .description("Warehouse loads")
                .tag("status", status)
                .register(registry)
                .record(Duration.between(startedAt, finishedAt));
        if ("SUCCEEDED".equals(status)) {
            lastSuccess.set(finishedAt.getEpochSecond());
        }
    }
}
