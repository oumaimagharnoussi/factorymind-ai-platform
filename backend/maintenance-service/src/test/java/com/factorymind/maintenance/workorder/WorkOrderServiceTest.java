package com.factorymind.maintenance.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factorymind.maintenance.alert.AlertEventMessage;
import com.factorymind.maintenance.common.BusinessRuleException;
import com.factorymind.maintenance.inventory.InventoryClient;
import com.factorymind.maintenance.inventory.IssuedStock;
import com.factorymind.maintenance.risk.RiskLevelChangedMessage;
import com.factorymind.maintenance.workorder.WorkOrderDtos.UsePartRequest;
import com.factorymind.maintenance.workorder.WorkOrderDtos.WorkOrderResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class WorkOrderServiceTest {

    private final WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
    private final InventoryClient inventory = mock(InventoryClient.class);
    private final List<Object> published = new ArrayList<>();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    private final WorkOrderService service = new WorkOrderService(workOrders, inventory, published::add, clock);

    private final UUID partId = UUID.randomUUID();
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        workOrder = new WorkOrder("WO-00007", UUID.randomUUID(), null, WorkOrderType.CORRECTIVE,
                WorkOrderPriority.HIGH, "Bearing noise", null, null, null, "operator", clock.instant());
        when(workOrders.findById(any())).thenReturn(Optional.of(workOrder));
        when(inventory.issue(eq(partId), anyInt(), any(), any(), any())).thenReturn(
                new IssuedStock(partId, "SKF-6309-2Z", "Bearing 6309-2Z", "pcs", 2, new BigDecimal("36.90"), 4));
    }

    @Test
    void bookingAPartTakesItOutOfStockFirst() {
        WorkOrderResponse response = service.usePart(UUID.randomUUID(), new UsePartRequest(partId, 2), "engineer");

        assertThat(response.parts()).singleElement().satisfies(line -> {
            assertThat(line.partNumber()).isEqualTo("SKF-6309-2Z");
            assertThat(line.cost()).isEqualByComparingTo("73.80");
        });
        assertThat(published).singleElement().isInstanceOfSatisfying(WorkOrderEventMessage.class,
                event -> assertThat(event.eventType()).isEqualTo(ActivityType.PART_USED));
        verify(inventory, never()).giveBack(any(), anyInt(), any(), any(), any());
    }

    @Test
    void aPartIsPutBackWhenTheWorkOrderCannotBeSaved() {
        when(workOrders.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(WorkOrder.class, "x"));

        assertThatThrownBy(() -> service.usePart(UUID.randomUUID(), new UsePartRequest(partId, 2), "engineer"))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        ArgumentCaptor<UUID> issueRequest = ArgumentCaptor.forClass(UUID.class);
        verify(inventory).issue(eq(partId), eq(2), issueRequest.capture(), any(), eq("WO-00007"));
        verify(inventory).giveBack(eq(partId), eq(2), eq(WorkOrderService.returnRequestId(issueRequest.getValue())),
                any(), eq("WO-00007"));
        assertThat(published).isEmpty();
    }

    @Test
    void noStockMovesForAClosedWorkOrder() {
        workOrder.moveTo(WorkOrderStatus.CANCELLED, null, "engineer", clock.instant());

        assertThatThrownBy(() -> service.usePart(UUID.randomUUID(), new UsePartRequest(partId, 1), "engineer"))
                .isInstanceOf(BusinessRuleException.class);
        verify(inventory, never()).issue(any(), anyInt(), any(), any(), any());
    }

    @Test
    void aCriticalAlertOpensOneUrgentWorkOrder() {
        UUID alertId = UUID.randomUUID();
        when(workOrders.existsForAlert(alertId)).thenReturn(false, true);
        when(workOrders.nextNumber()).thenReturn(42L);
        when(workOrders.save(any())).thenAnswer(call -> call.getArgument(0));
        AlertEventMessage alert = new AlertEventMessage("RAISED", alertId, UUID.randomUUID(), "ROTATION_SPEED",
                "CRITICAL", "OPEN", true, new BigDecimal("1688.20"), "rpm", new BigDecimal("1550"),
                new BigDecimal("1650"), clock.instant(), clock.instant());

        service.openForCriticalAlert(alert);
        service.openForCriticalAlert(alert);

        ArgumentCaptor<WorkOrder> saved = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrders).save(saved.capture());
        WorkOrder created = saved.getValue();
        assertThat(created.getCode()).isEqualTo("WO-00042");
        assertThat(created.getPriority()).isEqualTo(WorkOrderPriority.URGENT);
        assertThat(created.getTitle()).isEqualTo("Critical rotation speed alarm");
        assertThat(created.getDescription()).contains("1688.2 rpm", "critical limit 1650 rpm");
        assertThat(created.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(created.getRequestedBy()).isEqualTo("alert-service");
        assertThat(created.getAlertIds()).containsExactly(alertId);
    }

    @Test
    void aSecondCriticalSensorOnTheSameMachineJoinsTheOpenWorkOrder() {
        UUID first = UUID.randomUUID();
        WorkOrder running = new WorkOrder("WO-00042", UUID.randomUUID(), first, WorkOrderType.CORRECTIVE,
                WorkOrderPriority.URGENT, "Critical temperature alarm", null, null, null, "alert-service",
                clock.instant());
        when(workOrders.findOpenForAlertsOn(any(), any())).thenReturn(List.of(running));
        UUID second = UUID.randomUUID();

        service.openForCriticalAlert(new AlertEventMessage("RAISED", second, running.getAssetId(), "VIBRATION",
                "CRITICAL", "OPEN", true, new BigDecimal("7.6"), "mm/s", new BigDecimal("4.5"), new BigDecimal("7.1"),
                clock.instant(), clock.instant()));

        verify(workOrders, never()).save(any());
        assertThat(running.getAlertIds()).containsExactly(first, second);
        assertThat(running.getActivities()).last()
                .satisfies(activity -> {
                    assertThat(activity.getType()).isEqualTo(ActivityType.ALERT_LINKED);
                    assertThat(activity.getMessage()).isEqualTo("Critical vibration reached 7.6 mm/s (critical limit 7.1 mm/s)");
                });
        assertThat(published).singleElement().isInstanceOfSatisfying(WorkOrderEventMessage.class,
                event -> assertThat(event.alertIds()).containsExactlyInAnyOrder(first, second));
    }

    @Test
    void aMachineRatedHighRiskGetsOneInspectionBeforeItFails() {
        UUID assetId = UUID.randomUUID();
        when(workOrders.existsByAssetIdAndStatusIn(eq(assetId), any())).thenReturn(false);
        when(workOrders.nextNumber()).thenReturn(43L);
        when(workOrders.save(any())).thenAnswer(call -> call.getArgument(0));

        service.planInspectionForRisk(highRisk(assetId));

        ArgumentCaptor<WorkOrder> saved = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrders).save(saved.capture());
        WorkOrder created = saved.getValue();
        assertThat(created.getType()).isEqualTo(WorkOrderType.INSPECTION);
        assertThat(created.getPriority()).isEqualTo(WorkOrderPriority.HIGH);
        assertThat(created.getTitle()).isEqualTo("Predicted temperature failure");
        assertThat(created.getDescription()).contains("87% probability", "rf-24h-test",
                "trend reaches the critical limit in about 25 min");
        assertThat(created.getRequestedBy()).isEqualTo("ai-insights");
        assertThat(created.getAlertIds()).isEmpty();
        assertThat(published).singleElement().isInstanceOfSatisfying(WorkOrderEventMessage.class,
                event -> assertThat(event.eventType()).isEqualTo(ActivityType.CREATED));
    }

    @Test
    void noInspectionWhenTheMachineAlreadyHasAnOpenWorkOrder() {
        UUID assetId = UUID.randomUUID();
        when(workOrders.existsByAssetIdAndStatusIn(eq(assetId), any())).thenReturn(true);

        service.planInspectionForRisk(highRisk(assetId));

        verify(workOrders, never()).save(any());
        assertThat(published).isEmpty();
    }

    private RiskLevelChangedMessage highRisk(UUID assetId) {
        return new RiskLevelChangedMessage(assetId, "MEDIUM", "HIGH", 0.87, "TEMPERATURE", 0.42,
                List.of("trend reaches the critical limit in about 25 min"), "rf-24h-test", clock.instant());
    }
}
