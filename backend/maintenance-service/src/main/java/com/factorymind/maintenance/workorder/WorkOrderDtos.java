package com.factorymind.maintenance.workorder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorkOrderDtos {

    private WorkOrderDtos() {
    }

    /** @param alertId set when the work order answers an alert; an alert belongs to one work order only */
    public record CreateWorkOrderRequest(
            @NotNull UUID assetId,
            UUID alertId,
            @NotNull WorkOrderType type,
            @NotNull WorkOrderPriority priority,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) String description,
            @Size(max = 100) String assignee,
            LocalDate dueDate) {
    }

    /** The machine, type and alert of a work order are fixed once it exists. */
    public record UpdateWorkOrderRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) String description,
            @NotNull WorkOrderPriority priority,
            @Size(max = 100) String assignee,
            LocalDate dueDate) {
    }

    /** @param note required to complete (what was done); optional otherwise */
    public record StatusChangeRequest(@NotNull WorkOrderStatus status, @Size(max = 2000) String note) {
    }

    public record UsePartRequest(@NotNull UUID partId, @Min(1) @Max(10_000) int quantity) {
    }

    /** One row of the work order list (no parts or history). */
    public record WorkOrderSummary(
            UUID id,
            String code,
            UUID assetId,
            Set<UUID> alertIds,
            WorkOrderType type,
            WorkOrderPriority priority,
            WorkOrderStatus status,
            String title,
            String assignee,
            LocalDate dueDate,
            boolean overdue,
            String requestedBy,
            Instant createdAt,
            Instant completedAt) {

        static WorkOrderSummary of(WorkOrder w, LocalDate today) {
            return new WorkOrderSummary(w.getId(), w.getCode(), w.getAssetId(), Set.copyOf(w.getAlertIds()), w.getType(),
                    w.getPriority(), w.getStatus(), w.getTitle(), w.getAssignee(), w.getDueDate(), w.isOverdue(today),
                    w.getRequestedBy(), w.getCreatedAt(), w.getCompletedAt());
        }
    }

    public record PartLine(UUID id, UUID partId, String partNumber, String partName, String unit, int quantity,
                           BigDecimal unitCost, BigDecimal cost, String issuedBy, Instant issuedAt) {

        static PartLine of(WorkOrderPart p) {
            return new PartLine(p.getId(), p.getPartId(), p.getPartNumber(), p.getPartName(), p.getUnit(),
                    p.getQuantity(), p.getUnitCost(), p.cost(), p.getIssuedBy(), p.getIssuedAt());
        }
    }

    public record Activity(ActivityType type, String message, String actor, Instant occurredAt) {

        static Activity of(WorkOrderActivity a) {
            return new Activity(a.getType(), a.getMessage(), a.getActor(), a.getOccurredAt());
        }
    }

    /** @param nextStatuses the statuses this work order can move to now, so the UI shows only valid buttons */
    public record WorkOrderResponse(
            UUID id,
            String code,
            UUID assetId,
            Set<UUID> alertIds,
            WorkOrderType type,
            WorkOrderPriority priority,
            WorkOrderStatus status,
            Set<WorkOrderStatus> nextStatuses,
            String title,
            String description,
            String assignee,
            LocalDate dueDate,
            boolean overdue,
            String requestedBy,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant completedAt,
            String resolution,
            List<PartLine> parts,
            BigDecimal partsCost,
            List<Activity> activities,
            long version) {

        static WorkOrderResponse of(WorkOrder w, LocalDate today) {
            return new WorkOrderResponse(w.getId(), w.getCode(), w.getAssetId(), Set.copyOf(w.getAlertIds()), w.getType(),
                    w.getPriority(), w.getStatus(), w.getStatus().next(), w.getTitle(), w.getDescription(),
                    w.getAssignee(), w.getDueDate(), w.isOverdue(today), w.getRequestedBy(), w.getCreatedAt(),
                    w.getUpdatedAt(), w.getStartedAt(), w.getCompletedAt(), w.getResolution(),
                    w.getParts().stream().map(PartLine::of).toList(), w.partsCost(),
                    w.getActivities().stream().map(Activity::of).toList(), w.getVersion());
        }
    }

    /**
     * @param open             work orders not closed yet (OPEN, IN_PROGRESS, ON_HOLD)
     * @param overdue          open ones past their due date
     * @param mttrHours        mean time to repair of corrective work orders completed in the last 30 days,
     *                         from request to completion; null when there are none
     * @param completedLast30Days work orders completed in the last 30 days
     */
    public record MaintenanceSummary(Map<WorkOrderStatus, Long> byStatus, long open, long overdue, long urgentOpen,
                                     Double mttrHours, long completedLast30Days) {
    }
}
