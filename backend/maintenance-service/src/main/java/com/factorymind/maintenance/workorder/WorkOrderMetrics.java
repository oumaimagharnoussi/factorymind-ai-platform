package com.factorymind.maintenance.workorder;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Business metrics of the maintenance service, scraped by Prometheus from /actuator/prometheus:
 * <ul>
 *   <li>{@code factorymind_workorders_created_total{type,priority,origin}}: origin ALERT (critical alarm),
 *       AI_PREDICTION (failure-risk model), PLAN (preventive maintenance plan) or USER, which shows how much
 *       work is planned ahead rather than done after a failure;</li>
 *   <li>{@code factorymind_workorders_closed_total{status}}: completed or cancelled;</li>
 *   <li>{@code factorymind_workorders{status}} and {@code factorymind_workorders_overdue}: the backlog now.</li>
 * </ul>
 */
@Component
public class WorkOrderMetrics {

    private static final List<WorkOrderStatus> OPEN = List.of(
            WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD);

    private final MeterRegistry registry;

    public WorkOrderMetrics(MeterRegistry registry, WorkOrderRepository workOrders, Clock clock) {
        this.registry = registry;
        Cached<Map<WorkOrderStatus, Long>> byStatus = new Cached<>(Duration.ofSeconds(10), () -> {
            Map<WorkOrderStatus, Long> counts = new EnumMap<>(WorkOrderStatus.class);
            for (WorkOrderStatus status : OPEN) {
                counts.put(status, 0L);
            }
            workOrders.countByStatus().forEach(row -> {
                WorkOrderStatus status = row.getKey() instanceof WorkOrderStatus s ? s
                        : WorkOrderStatus.valueOf(String.valueOf(row.getKey()));
                counts.put(status, row.getTotal());
            });
            return counts;
        });
        for (WorkOrderStatus status : OPEN) {
            // Gauges keep only a weak reference to their state by default; the cache lives nowhere else.
            Gauge.builder("factorymind.workorders", byStatus, c -> c.get().getOrDefault(status, 0L))
                    .description("Work orders not closed yet, by status")
                    .tag("status", status.name())
                    .strongReference(true)
                    .register(registry);
        }
        Cached<Long> overdue = new Cached<>(Duration.ofSeconds(10), () -> workOrders.countByStatusInAndDueDateBefore(
                OPEN, LocalDate.now(clock)));
        Gauge.builder("factorymind.workorders.overdue", overdue, Cached::get)
                .description("Open work orders past their due date")
                .strongReference(true)
                .register(registry);
    }

    /** Counted once the change is committed, like the Kafka event that announces it. */
    @TransactionalEventListener
    void onWorkOrderEvent(WorkOrderEventMessage event) {
        if (event.eventType() == ActivityType.CREATED) {
            Counter.builder("factorymind.workorders.created")
                    .description("Work orders opened, by who asked for them")
                    .tag("type", event.type().name())
                    .tag("priority", event.priority().name())
                    .tag("origin", origin(event.actor()))
                    .register(registry)
                    .increment();
        } else if (event.eventType() == ActivityType.STATUS_CHANGED && event.status().isClosed()) {
            Counter.builder("factorymind.workorders.closed")
                    .description("Work orders completed or cancelled")
                    .tag("status", event.status().name())
                    .tag("type", event.type().name())
                    .register(registry)
                    .increment();
        }
    }

    static String origin(String actor) {
        if (WorkOrderService.ALERT_SERVICE.equals(actor)) {
            return "ALERT";
        }
        if (WorkOrderService.AI_INSIGHTS.equals(actor)) {
            return "AI_PREDICTION";
        }
        if (WorkOrderService.PLANNER.equals(actor)) {
            return "PLAN";
        }
        return "USER";
    }

    /** Re-reads the database at most once per period, however often Prometheus scrapes. */
    static final class Cached<T> {
        private final Duration maxAge;
        private final Supplier<T> loader;
        private T value;
        private Instant loadedAt = Instant.MIN;

        Cached(Duration maxAge, Supplier<T> loader) {
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
