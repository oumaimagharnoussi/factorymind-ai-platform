package com.factorymind.maintenance.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factorymind.maintenance.common.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkOrderTest {

    private final Instant now = Instant.parse("2026-10-27T10:00:00Z");

    @Test
    void followsTheLifeCycle() {
        WorkOrder workOrder = newWorkOrder();

        workOrder.moveTo(WorkOrderStatus.IN_PROGRESS, null, "engineer", now);
        workOrder.moveTo(WorkOrderStatus.ON_HOLD, "Waiting for the bearing", "engineer", now.plusSeconds(60));
        workOrder.moveTo(WorkOrderStatus.IN_PROGRESS, null, "engineer", now.plusSeconds(3600));
        workOrder.moveTo(WorkOrderStatus.COMPLETED, "Bearing replaced", "engineer", now.plusSeconds(7200));

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(workOrder.getStartedAt()).as("first start is kept").isEqualTo(now);
        assertThat(workOrder.getCompletedAt()).isEqualTo(now.plusSeconds(7200));
        assertThat(workOrder.getResolution()).isEqualTo("Bearing replaced");
        assertThat(workOrder.getAssignee()).as("whoever starts an unassigned job takes it").isEqualTo("engineer");
        assertThat(workOrder.getActivities()).extracting(WorkOrderActivity::getType).containsExactly(
                ActivityType.CREATED, ActivityType.STATUS_CHANGED, ActivityType.STATUS_CHANGED,
                ActivityType.STATUS_CHANGED, ActivityType.STATUS_CHANGED);
    }

    @Test
    void cannotSkipStepsOrReopen() {
        WorkOrder workOrder = newWorkOrder();

        assertThatThrownBy(() -> workOrder.moveTo(WorkOrderStatus.COMPLETED, "done", "engineer", now))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A work order cannot go from OPEN to COMPLETED");

        workOrder.moveTo(WorkOrderStatus.CANCELLED, "Duplicate request", "engineer", now);
        assertThatThrownBy(() -> workOrder.moveTo(WorkOrderStatus.IN_PROGRESS, null, "engineer", now))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(WorkOrderStatus.CANCELLED.next()).isEmpty();
    }

    @Test
    void completingNeedsADescriptionOfTheWork() {
        WorkOrder workOrder = newWorkOrder();
        workOrder.moveTo(WorkOrderStatus.IN_PROGRESS, null, "engineer", now);

        assertThatThrownBy(() -> workOrder.moveTo(WorkOrderStatus.COMPLETED, "  ", "engineer", now))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Describe what was found");
    }

    @Test
    void addsUpThePartsCostAndRefusesPartsOnceClosed() {
        WorkOrder workOrder = newWorkOrder();
        workOrder.addPart(part(2, "36.90"), "engineer", now);
        workOrder.addPart(part(5, "22.00"), "engineer", now);

        assertThat(workOrder.partsCost()).isEqualByComparingTo("183.80");

        workOrder.moveTo(WorkOrderStatus.CANCELLED, null, "engineer", now);
        assertThatThrownBy(() -> workOrder.addPart(part(1, "1.00"), "engineer", now))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A cancelled work order cannot be given parts");
    }

    @Test
    void isOverdueOnlyWhileOpen() {
        WorkOrder workOrder = newWorkOrder();
        LocalDate dayAfterDue = LocalDate.of(2026, 10, 29);

        assertThat(workOrder.isOverdue(dayAfterDue)).isTrue();
        workOrder.moveTo(WorkOrderStatus.CANCELLED, null, "engineer", now);
        assertThat(workOrder.isOverdue(dayAfterDue)).isFalse();
    }

    private WorkOrder newWorkOrder() {
        return new WorkOrder("WO-00001", UUID.randomUUID(), null, WorkOrderType.CORRECTIVE, WorkOrderPriority.HIGH,
                "Bearing noise", null, null, LocalDate.of(2026, 10, 28), "operator", now);
    }

    private static WorkOrderPart part(int quantity, String unitCost) {
        return new WorkOrderPart(UUID.randomUUID(), UUID.randomUUID(), "SKF-6309-2Z", "Bearing", "pcs", quantity,
                new BigDecimal(unitCost), "engineer", Instant.parse("2026-10-27T10:00:00Z"));
    }
}
