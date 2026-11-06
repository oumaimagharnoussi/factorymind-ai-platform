package com.factorymind.maintenance.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factorymind.maintenance.common.BusinessRuleException;
import com.factorymind.maintenance.common.ConflictException;
import com.factorymind.maintenance.plan.PlanDtos.PlanRequest;
import com.factorymind.maintenance.workorder.ActivityType;
import com.factorymind.maintenance.workorder.PlannedJob;
import com.factorymind.maintenance.workorder.WorkOrderEventMessage;
import com.factorymind.maintenance.workorder.WorkOrderPriority;
import com.factorymind.maintenance.workorder.WorkOrderRepository;
import com.factorymind.maintenance.workorder.WorkOrderService;
import com.factorymind.maintenance.workorder.WorkOrderStatus;
import com.factorymind.maintenance.workorder.WorkOrderType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlanServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-06");

    private final MaintenancePlanRepository plans = mock(MaintenancePlanRepository.class);
    private final WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
    private final WorkOrderService workOrderService = mock(WorkOrderService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T09:00:00Z"), ZoneOffset.UTC);
    private final PlanService service = new PlanService(plans, workOrders, workOrderService, clock);

    @Test
    void aDuePlanOpensOnePreventiveWorkOrderWithItsInstructions() {
        MaintenancePlan plan = MaintenancePlanTest.plan(PlanSchedule.FIXED, 30, 3, TODAY.plusDays(2));
        UUID id = UUID.randomUUID();
        when(plans.findById(id)).thenReturn(Optional.of(plan));

        service.openIfDue(id);

        ArgumentCaptor<PlannedJob> job = ArgumentCaptor.forClass(PlannedJob.class);
        verify(workOrderService).openForPlan(job.capture());
        assertThat(job.getValue().dueDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(job.getValue().title()).isEqualTo("Lubricate bearings");
        assertThat(job.getValue().assignee()).isEqualTo("engineer");
        assertThat(job.getValue().description()).startsWith("8 g each side")
                .contains("PM-00001: every 30 days on the calendar");
        assertThat(plan.getNextDueDate()).isEqualTo(TODAY.plusDays(32));
    }

    @Test
    void nothingIsOpenedBeforeTheLeadTimeOrWhileTheLastJobIsOpen() {
        UUID early = UUID.randomUUID();
        UUID busy = UUID.randomUUID();
        when(plans.findById(early)).thenReturn(Optional.of(
                MaintenancePlanTest.plan(PlanSchedule.FIXED, 30, 3, TODAY.plusDays(4))));
        when(plans.findById(busy)).thenReturn(Optional.of(
                MaintenancePlanTest.plan(PlanSchedule.FIXED, 30, 3, TODAY)));
        when(workOrders.existsByPlanIdAndStatusIn(eq(busy), anyCollection())).thenReturn(true);

        assertThat(service.openIfDue(early)).isNull();
        assertThat(service.openIfDue(busy)).isNull();
        assertThat(service.openIfDue(UUID.randomUUID())).isNull();
        verify(workOrderService, never()).openForPlan(any());
    }

    @Test
    void anEngineerCanOpenTheNextJobEarlyButNotASecondOne() {
        MaintenancePlan plan = MaintenancePlanTest.plan(PlanSchedule.FLOATING, 90, 7, TODAY.plusDays(40));
        UUID id = UUID.randomUUID();
        when(plans.findById(id)).thenReturn(Optional.of(plan));

        service.openNow(id, "engineer");

        ArgumentCaptor<PlannedJob> job = ArgumentCaptor.forClass(PlannedJob.class);
        verify(workOrderService).openForPlan(job.capture());
        assertThat(job.getValue().dueDate()).isEqualTo(TODAY);

        // The plan is not saved in this test, so its id is null.
        when(workOrders.existsByPlanIdAndStatusIn(any(), anyCollection())).thenReturn(true);
        assertThatThrownBy(() -> service.openNow(id, "engineer")).isInstanceOf(ConflictException.class);
        plan.pause();
        assertThatThrownBy(() -> service.openNow(id, "engineer"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("paused");
    }

    @Test
    void aPlanCannotStartInThePastNorMoveToAnotherMachine() {
        UUID machine = UUID.randomUUID();
        assertThatThrownBy(() -> service.create(request(machine, TODAY.minusDays(1)), "engineer"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("past");

        MaintenancePlan plan = MaintenancePlanTest.plan(PlanSchedule.FIXED, 30, 3, TODAY.plusDays(4));
        UUID id = UUID.randomUUID();
        when(plans.findById(id)).thenReturn(Optional.of(plan));
        assertThatThrownBy(() -> service.update(id, request(UUID.randomUUID(), TODAY), "engineer"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("machine");
    }

    @Test
    void theStateTellsWhatThePlannerWillDo() {
        MaintenancePlan due = MaintenancePlanTest.plan(PlanSchedule.FIXED, 30, 3, TODAY.plusDays(1));
        MaintenancePlan later = MaintenancePlanTest.plan(PlanSchedule.FIXED, 30, 3, TODAY.plusDays(20));
        MaintenancePlan paused = MaintenancePlanTest.plan(PlanSchedule.FIXED, 30, 3, TODAY);
        paused.pause();
        when(plans.findAllByOrderByNextDueDateAscCodeAsc()).thenReturn(List.of(due, later, paused));

        assertThat(service.list(null, null)).extracting(PlanDtos.PlanResponse::state)
                .containsExactly(PlanState.DUE, PlanState.SCHEDULED, PlanState.PAUSED);
        assertThat(service.list(null, true)).hasSize(2);
        assertThat(service.list(due.getAssetId(), null)).singleElement()
                .satisfies(p -> assertThat(p.opensOn()).isEqualTo(TODAY.minusDays(2)));
    }

    @Test
    void closingAFloatingPlansJobSetsItsNextDate() {
        MaintenancePlan plan = MaintenancePlanTest.plan(PlanSchedule.FLOATING, 90, 7, TODAY);
        UUID id = UUID.randomUUID();
        when(plans.findById(id)).thenReturn(Optional.of(plan));
        PlanJobListener listener = new PlanJobListener(service);

        listener.onWorkOrderEvent(event(ActivityType.STATUS_CHANGED, WorkOrderStatus.IN_PROGRESS, id));
        assertThat(plan.getNextDueDate()).isEqualTo(TODAY);
        listener.onWorkOrderEvent(event(ActivityType.STATUS_CHANGED, WorkOrderStatus.COMPLETED, null));
        assertThat(plan.getNextDueDate()).isEqualTo(TODAY);

        listener.onWorkOrderEvent(event(ActivityType.STATUS_CHANGED, WorkOrderStatus.COMPLETED, id));
        assertThat(plan.getNextDueDate()).isEqualTo(LocalDate.parse("2026-11-08").plusDays(90));
    }

    private static PlanRequest request(UUID machine, LocalDate dueDate) {
        return new PlanRequest(machine, "Lubricate bearings", null, PlanSchedule.FIXED, 30, 3,
                WorkOrderPriority.MEDIUM, null, dueDate);
    }

    private static WorkOrderEventMessage event(ActivityType type, WorkOrderStatus status, UUID planId) {
        return new WorkOrderEventMessage(type, UUID.randomUUID(), "WO-00009", UUID.randomUUID(), Set.of(),
                WorkOrderType.PREVENTIVE, WorkOrderPriority.MEDIUM, status, "Lubricate bearings", "engineer",
                "engineer", planId, Instant.parse("2026-11-08T15:30:00Z"));
    }
}
