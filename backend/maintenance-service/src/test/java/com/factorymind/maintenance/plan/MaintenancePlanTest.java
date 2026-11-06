package com.factorymind.maintenance.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factorymind.maintenance.common.BusinessRuleException;
import com.factorymind.maintenance.workorder.WorkOrderPriority;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenancePlanTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-06");

    @Test
    void theWorkOrderOpensLeadDaysBeforeTheDueDate() {
        MaintenancePlan plan = plan(PlanSchedule.FIXED, 30, 3, TODAY.plusDays(5));

        assertThat(plan.opensOn()).isEqualTo(TODAY.plusDays(2));
        assertThat(plan.isDue(TODAY.plusDays(1))).isFalse();
        assertThat(plan.isDue(TODAY.plusDays(2))).isTrue();
        assertThat(plan.isDue(TODAY.plusDays(40))).isTrue();
        plan.pause();
        assertThat(plan.isDue(TODAY.plusDays(40))).isFalse();
    }

    @Test
    void aFixedPlanMovesOnTheCalendarAsSoonAsItsJobIsOpened() {
        MaintenancePlan plan = plan(PlanSchedule.FIXED, 30, 3, TODAY.plusDays(2));

        assertThat(plan.dueDateWhenOpenedOn(TODAY)).isEqualTo(TODAY.plusDays(2));
        plan.occurrenceOpened(Instant.parse("2026-11-06T08:00:00Z"));
        assertThat(plan.getNextDueDate()).isEqualTo(TODAY.plusDays(32));
        assertThat(plan.getLastGeneratedAt()).isEqualTo(Instant.parse("2026-11-06T08:00:00Z"));

        // Done ten days late: the calendar does not move.
        plan.jobClosed(TODAY.plusDays(12));
        assertThat(plan.getNextDueDate()).isEqualTo(TODAY.plusDays(32));
    }

    @Test
    void aFloatingPlanCountsFromTheDayItsJobWasClosed() {
        MaintenancePlan plan = plan(PlanSchedule.FLOATING, 90, 7, TODAY.plusDays(3));

        plan.occurrenceOpened(Instant.parse("2026-11-06T08:00:00Z"));
        assertThat(plan.getNextDueDate()).isEqualTo(TODAY.plusDays(3));

        plan.jobClosed(TODAY.plusDays(10));
        assertThat(plan.getNextDueDate()).isEqualTo(TODAY.plusDays(100));
    }

    @Test
    void openedAheadOfTimeTheJobIsDueToday() {
        MaintenancePlan plan = plan(PlanSchedule.FIXED, 30, 3, TODAY.plusDays(20));

        assertThat(plan.dueDateWhenOpenedOn(TODAY)).isEqualTo(TODAY);
    }

    @Test
    void aPlanResumedAfterALongPauseStartsAgainFromToday() {
        MaintenancePlan plan = plan(PlanSchedule.FIXED, 7, 1, TODAY.minusDays(60));
        plan.pause();

        plan.resume(TODAY);

        assertThat(plan.isActive()).isTrue();
        assertThat(plan.getNextDueDate()).isEqualTo(TODAY);
    }

    @Test
    void theLeadTimeMustBeShorterThanTheInterval() {
        assertThatThrownBy(() -> plan(PlanSchedule.FIXED, 7, 7, TODAY))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("fewer than 7 days");
        assertThatThrownBy(() -> plan(PlanSchedule.FIXED, 0, 0, TODAY))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("interval");
    }

    @Test
    void textIsTrimmedAndBlankOptionalFieldsAreCleared() {
        MaintenancePlan plan = new MaintenancePlan("PM-00001", UUID.randomUUID(), "  Grease bearings ", " ",
                PlanSchedule.FIXED, 30, 0, WorkOrderPriority.LOW, "", TODAY, "engineer");

        assertThat(plan.getTitle()).isEqualTo("Grease bearings");
        assertThat(plan.getInstructions()).isNull();
        assertThat(plan.getAssignee()).isNull();
        assertThat(plan.isActive()).isTrue();
    }

    static MaintenancePlan plan(PlanSchedule schedule, int intervalDays, int leadDays, LocalDate dueDate) {
        return new MaintenancePlan("PM-00001", UUID.randomUUID(), "Lubricate bearings", "8 g each side", schedule,
                intervalDays, leadDays, WorkOrderPriority.MEDIUM, "engineer", dueDate, "engineer");
    }
}
