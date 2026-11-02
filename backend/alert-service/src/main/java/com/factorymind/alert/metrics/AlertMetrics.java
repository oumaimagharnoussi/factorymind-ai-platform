package com.factorymind.alert.metrics;

import com.factorymind.alert.alert.AlertDtos.AlertEvent;
import com.factorymind.alert.alert.AlertDtos.AlertResponse;
import com.factorymind.alert.alert.AlertEventType;
import com.factorymind.alert.alert.AlertRepository;
import com.factorymind.alert.alert.AlertSeverity;
import com.factorymind.alert.alert.AlertStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Business metrics of the alert service, scraped by Prometheus from /actuator/prometheus:
 * <ul>
 *   <li>{@code factorymind_alerts_events_total{type,severity}}: committed alert changes (raised, escalated...);</li>
 *   <li>{@code factorymind_alerts_unresolved{severity}}, {@code factorymind_alerts_active},
 *       {@code factorymind_alerts_unacknowledged}: the alert board right now;</li>
 *   <li>{@code factorymind_alerts_time_to_acknowledge_seconds}: how long operators take to react.</li>
 * </ul>
 */
@Component
public class AlertMetrics {

    private final MeterRegistry registry;
    private final Timer timeToAcknowledge;

    public AlertMetrics(MeterRegistry registry, AlertRepository alerts) {
        this.registry = registry;
        CachedValue<Board> board = new CachedValue<>(Duration.ofSeconds(10), () -> new Board(
                alerts.countBySeverityAndStatusNot(AlertSeverity.CRITICAL, AlertStatus.RESOLVED),
                alerts.countBySeverityAndStatusNot(AlertSeverity.WARNING, AlertStatus.RESOLVED),
                alerts.countByActiveTrueAndStatusNot(AlertStatus.RESOLVED),
                alerts.countByStatus(AlertStatus.OPEN)));
        // Gauges keep only a weak reference to their state by default; the cache lives nowhere else.
        Gauge.builder("factorymind.alerts.unresolved", board, b -> b.get().critical())
                .description("Alerts not resolved yet").tag("severity", AlertSeverity.CRITICAL.name())
                .strongReference(true)
                .register(registry);
        Gauge.builder("factorymind.alerts.unresolved", board, b -> b.get().warning())
                .description("Alerts not resolved yet").tag("severity", AlertSeverity.WARNING.name())
                .strongReference(true)
                .register(registry);
        Gauge.builder("factorymind.alerts.active", board, b -> b.get().active())
                .description("Unresolved alerts whose condition is still present")
                .strongReference(true)
                .register(registry);
        Gauge.builder("factorymind.alerts.unacknowledged", board, b -> b.get().unacknowledged())
                .description("Alerts nobody has acknowledged yet")
                .strongReference(true)
                .register(registry);
        timeToAcknowledge = Timer.builder("factorymind.alerts.time.to.acknowledge")
                .description("Time from an alert being raised to its acknowledgement")
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofSeconds(1))
                .maximumExpectedValue(Duration.ofHours(8))
                .register(registry);
    }

    /** Counted once the change is committed, like the Kafka event that announces it. */
    @TransactionalEventListener
    void onAlertEvent(AlertEvent event) {
        AlertResponse alert = event.alert();
        Counter.builder("factorymind.alerts.events")
                .description("Committed alert changes")
                .tag("type", event.type().name())
                .tag("severity", alert.severity().name())
                .register(registry)
                .increment();
        if (event.type() == AlertEventType.ACKNOWLEDGED && alert.raisedAt() != null && alert.acknowledgedAt() != null) {
            Duration waited = Duration.between(alert.raisedAt(), alert.acknowledgedAt());
            if (!waited.isNegative()) {
                timeToAcknowledge.record(waited);
            }
        }
    }

    private record Board(long critical, long warning, long active, long unacknowledged) {
    }

    /** Re-reads the database at most once per period, however often Prometheus scrapes. */
    static final class CachedValue<T> {
        private final Duration maxAge;
        private final Supplier<T> loader;
        private T value;
        private Instant loadedAt = Instant.MIN;

        CachedValue(Duration maxAge, Supplier<T> loader) {
            this.maxAge = maxAge;
            this.loader = loader;
        }

        synchronized T get() {
            Instant now = Instant.now();
            if (value == null || loadedAt.plus(maxAge).isBefore(now)) {
                value = loader.get();
                loadedAt = now;
            }
            return value;
        }
    }
}
