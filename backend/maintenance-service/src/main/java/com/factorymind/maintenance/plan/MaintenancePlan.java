package com.factorymind.maintenance.plan;

import com.factorymind.maintenance.common.BaseEntity;
import com.factorymind.maintenance.common.BusinessRuleException;
import com.factorymind.maintenance.workorder.WorkOrderPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A preventive job to repeat on one machine every {@code intervalDays}. Each occurrence becomes a PREVENTIVE work
 * order, opened {@code leadDays} before it is due so the job can be scheduled. A plan has at most one open work
 * order: if the last job is not closed yet, the next one waits rather than piling up.
 */
@Entity
@Table(name = "maintenance_plans")
public class MaintenancePlan extends BaseEntity {

    static final int MAX_INTERVAL_DAYS = 3650;

    /** Human-friendly number: PM-00007. */
    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String code;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(nullable = false, length = 160)
    private String title;

    /** What the technician has to do; copied into each work order. */
    @Column(length = 2000)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlanSchedule schedule;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(name = "lead_days", nullable = false)
    private int leadDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WorkOrderPriority priority;

    /** Technician who gets the work orders, if the plan has a usual one. */
    @Column(length = 100)
    private String assignee;

    @Column(nullable = false)
    private boolean active;

    /** When the next occurrence is due; its work order opens {@code leadDays} earlier. */
    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "last_generated_at")
    private Instant lastGeneratedAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    protected MaintenancePlan() {
    }

    public MaintenancePlan(String code, UUID assetId, String title, String instructions, PlanSchedule schedule,
                           int intervalDays, int leadDays, WorkOrderPriority priority, String assignee,
                           LocalDate firstDueDate, String createdBy) {
        this.code = code;
        this.assetId = assetId;
        this.createdBy = createdBy;
        this.active = true;
        apply(title, instructions, schedule, intervalDays, leadDays, priority, assignee, firstDueDate);
    }

    public void update(String title, String instructions, PlanSchedule schedule, int intervalDays, int leadDays,
                       WorkOrderPriority priority, String assignee, LocalDate nextDueDate) {
        apply(title, instructions, schedule, intervalDays, leadDays, priority, assignee, nextDueDate);
    }

    public void pause() {
        active = false;
    }

    /** A plan paused for a long time restarts from today: missed occurrences are not opened one after another. */
    public void resume(LocalDate today) {
        active = true;
        if (nextDueDate.isBefore(today)) {
            nextDueDate = today;
        }
    }

    /** The day the planner opens the work order of the next occurrence. */
    public LocalDate opensOn() {
        return nextDueDate.minusDays(leadDays);
    }

    /** Active and inside its lead time (or late). Whether a job is still open is checked by the planner. */
    public boolean isDue(LocalDate today) {
        return active && !opensOn().isAfter(today);
    }

    /**
     * The due date of the work order opened now: the occurrence's own date, or today when an engineer opens it
     * ahead of time (for example while the machine is stopped anyway).
     */
    public LocalDate dueDateWhenOpenedOn(LocalDate today) {
        return isDue(today) ? nextDueDate : today;
    }

    /**
     * The work order of the current occurrence was opened. A FIXED plan moves to the next calendar date at once;
     * a FLOATING one waits for the job to be closed.
     */
    public void occurrenceOpened(Instant now) {
        lastGeneratedAt = now;
        if (schedule == PlanSchedule.FIXED) {
            nextDueDate = nextDueDate.plusDays(intervalDays);
        }
    }

    /** Its work order was completed or cancelled: a FLOATING plan counts the interval from that day. */
    public void jobClosed(LocalDate closedOn) {
        if (schedule == PlanSchedule.FLOATING) {
            nextDueDate = closedOn.plusDays(intervalDays);
        }
    }

    private void apply(String title, String instructions, PlanSchedule schedule, int intervalDays, int leadDays,
                       WorkOrderPriority priority, String assignee, LocalDate nextDueDate) {
        if (intervalDays < 1 || intervalDays > MAX_INTERVAL_DAYS) {
            throw new BusinessRuleException("The interval must be between 1 and " + MAX_INTERVAL_DAYS + " days");
        }
        if (leadDays < 0 || leadDays >= intervalDays) {
            throw new BusinessRuleException("The work order must open at least 0 and fewer than " + intervalDays
                    + " days before it is due");
        }
        this.title = title.strip();
        this.instructions = blankToNull(instructions);
        this.schedule = schedule;
        this.intervalDays = intervalDays;
        this.leadDays = leadDays;
        this.priority = priority;
        this.assignee = blankToNull(assignee);
        this.nextDueDate = nextDueDate;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public String getCode() {
        return code;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getTitle() {
        return title;
    }

    public String getInstructions() {
        return instructions;
    }

    public PlanSchedule getSchedule() {
        return schedule;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public int getLeadDays() {
        return leadDays;
    }

    public WorkOrderPriority getPriority() {
        return priority;
    }

    public String getAssignee() {
        return assignee;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public Instant getLastGeneratedAt() {
        return lastGeneratedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
