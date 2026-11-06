package com.factorymind.maintenance.plan;

import com.factorymind.maintenance.workorder.WorkOrderPriority;
import com.factorymind.maintenance.workorder.WorkOrderStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PlanDtos {

    private PlanDtos() {
    }

    /**
     * @param intervalDays how often the job is repeated
     * @param leadDays     how many days before the due date its work order opens (fewer than the interval)
     * @param nextDueDate  when the next (on creation: the first) occurrence is due; not in the past
     */
    public record PlanRequest(
            @NotNull UUID assetId,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) String instructions,
            @NotNull PlanSchedule schedule,
            @NotNull @Min(1) @Max(MaintenancePlan.MAX_INTERVAL_DAYS) Integer intervalDays,
            @NotNull @Min(0) @Max(MaintenancePlan.MAX_INTERVAL_DAYS - 1) Integer leadDays,
            @NotNull WorkOrderPriority priority,
            @Size(max = 100) String assignee,
            @NotNull LocalDate nextDueDate) {
    }

    /** The plan's job that is not closed yet. */
    public record CurrentJob(UUID id, String code, WorkOrderStatus status, LocalDate dueDate, boolean overdue) {
    }

    /**
     * @param opensOn         the day the planner opens the next work order (next due date minus lead days)
     * @param currentJob      the open work order of the plan, or null
     * @param lastCompletedAt when a work order of this plan was last completed, or null
     */
    public record PlanResponse(
            UUID id,
            String code,
            UUID assetId,
            String title,
            String instructions,
            PlanSchedule schedule,
            int intervalDays,
            int leadDays,
            WorkOrderPriority priority,
            String assignee,
            boolean active,
            PlanState state,
            LocalDate nextDueDate,
            LocalDate opensOn,
            CurrentJob currentJob,
            Instant lastCompletedAt,
            Instant lastGeneratedAt,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
