package com.factorymind.maintenance.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkOrderMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
    private final WorkOrderMetrics metrics = new WorkOrderMetrics(registry, workOrders, clock);

    @Test
    void workOrdersAreCountedByWhoAskedForThem() {
        metrics.onWorkOrderEvent(event(ActivityType.CREATED, WorkOrderType.CORRECTIVE, WorkOrderStatus.OPEN,
                WorkOrderService.ALERT_SERVICE));
        metrics.onWorkOrderEvent(event(ActivityType.CREATED, WorkOrderType.INSPECTION, WorkOrderStatus.OPEN,
                WorkOrderService.AI_INSIGHTS));
        metrics.onWorkOrderEvent(event(ActivityType.CREATED, WorkOrderType.PREVENTIVE, WorkOrderStatus.OPEN, "engineer"));
        metrics.onWorkOrderEvent(event(ActivityType.CREATED, WorkOrderType.PREVENTIVE, WorkOrderStatus.OPEN,
                WorkOrderService.PLANNER));
        metrics.onWorkOrderEvent(event(ActivityType.STATUS_CHANGED, WorkOrderType.INSPECTION, WorkOrderStatus.COMPLETED,
                "engineer"));
        metrics.onWorkOrderEvent(event(ActivityType.STATUS_CHANGED, WorkOrderType.INSPECTION, WorkOrderStatus.IN_PROGRESS,
                "engineer"));

        assertThat(created("ALERT")).isEqualTo(1);
        assertThat(created("AI_PREDICTION")).isEqualTo(1);
        assertThat(created("USER")).isEqualTo(1);
        assertThat(created("PLAN")).isEqualTo(1);
        assertThat(registry.get("factorymind.workorders.closed").tag("status", "COMPLETED").counter().count()).isEqualTo(1);
        assertThat(registry.find("factorymind.workorders.closed").tag("status", "IN_PROGRESS").counter()).isNull();
    }

    @Test
    void theBacklogGaugesReadTheCountsByStatus() {
        WorkOrderRepository.CountByKey open = count(WorkOrderStatus.OPEN, 4);
        WorkOrderRepository.CountByKey done = count(WorkOrderStatus.COMPLETED, 9);
        when(workOrders.countByStatus()).thenReturn(List.of(open, done));

        System.gc();

        assertThat(registry.get("factorymind.workorders").tag("status", "OPEN").gauge().value()).isEqualTo(4);
        assertThat(registry.get("factorymind.workorders").tag("status", "ON_HOLD").gauge().value()).isZero();
        assertThat(registry.find("factorymind.workorders").tag("status", "COMPLETED").gauge()).isNull();
    }

    private double created(String origin) {
        return registry.get("factorymind.workorders.created").tag("origin", origin).counter().count();
    }

    private static WorkOrderEventMessage event(ActivityType type, WorkOrderType workType, WorkOrderStatus status,
                                               String actor) {
        return new WorkOrderEventMessage(type, UUID.randomUUID(), "WO-1", UUID.randomUUID(), Set.of(), workType,
                WorkOrderPriority.HIGH, status, "title", null, actor, null, Instant.now());
    }

    private static WorkOrderRepository.CountByKey count(WorkOrderStatus status, long total) {
        return new WorkOrderRepository.CountByKey() {
            @Override
            public Object getKey() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
