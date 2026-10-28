package com.factorymind.maintenance.workorder;

import com.factorymind.maintenance.common.BaseEntity;
import com.factorymind.maintenance.common.BusinessRuleException;
import com.factorymind.maintenance.common.NotFoundException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A maintenance job on one machine. The machine is referenced by its asset id (asset-service), the alerts
 * it answers by their alert ids (alert-service): ids only, never foreign keys across services.
 */
@Entity
@Table(name = "work_orders")
public class WorkOrder extends BaseEntity {

    /** Human-friendly number shown on screens and paper: WO-00042. */
    @Column(nullable = false, unique = true, updatable = false, length = 20)
    private String code;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    /**
     * Alerts this job answers. One failing machine often trips several sensors; they all land on the same
     * work order instead of one work order per sensor. An alert belongs to at most one work order.
     */
    @ElementCollection
    @CollectionTable(name = "work_order_alerts", joinColumns = @JoinColumn(name = "work_order_id"))
    @Column(name = "alert_id")
    @BatchSize(size = 50)
    private Set<UUID> alertIds = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private WorkOrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WorkOrderPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private WorkOrderStatus status;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 2000)
    private String description;

    /** Keycloak user name of the technician in charge. */
    @Column(length = 100)
    private String assignee;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** What was found and done; required to complete. */
    @Column(length = 2000)
    private String resolution;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("issuedAt")
    private List<WorkOrderPart> parts = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt")
    private List<WorkOrderActivity> activities = new ArrayList<>();

    protected WorkOrder() {
    }

    public WorkOrder(String code, UUID assetId, UUID alertId, WorkOrderType type, WorkOrderPriority priority,
                     String title, String description, String assignee, LocalDate dueDate, String requestedBy,
                     Instant now) {
        this.code = code;
        this.assetId = assetId;
        if (alertId != null) {
            alertIds.add(alertId);
        }
        this.type = type;
        this.priority = priority;
        this.status = WorkOrderStatus.OPEN;
        this.title = title;
        this.description = description;
        this.assignee = blankToNull(assignee);
        this.dueDate = dueDate;
        this.requestedBy = requestedBy;
        log(ActivityType.CREATED, alertId == null ? "Work order created" : "Work order created for an alert",
                requestedBy, now);
    }

    /** Another alert on the same machine that this job will take care of too. */
    public void linkAlert(UUID alertId, String description, String actor, Instant now) {
        ensureOpen("linked to an alert");
        if (alertIds.add(alertId)) {
            log(ActivityType.ALERT_LINKED, description, actor, now);
        }
    }

    public void update(String title, String description, WorkOrderPriority priority, String assignee,
                       LocalDate dueDate, String actor, Instant now) {
        ensureOpen("edited");
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.assignee = blankToNull(assignee);
        this.dueDate = dueDate;
        log(ActivityType.UPDATED, "Details updated" + (this.assignee == null ? "" : ", assigned to " + this.assignee),
                actor, now);
    }

    public void moveTo(WorkOrderStatus target, String note, String actor, Instant now) {
        if (!status.canMoveTo(target)) {
            throw new BusinessRuleException("A work order cannot go from " + status + " to " + target);
        }
        if (target == WorkOrderStatus.COMPLETED && (note == null || note.isBlank())) {
            throw new BusinessRuleException("Describe what was found and done before completing the work order");
        }
        WorkOrderStatus from = status;
        status = target;
        if (target == WorkOrderStatus.IN_PROGRESS && startedAt == null) {
            startedAt = now;
            if (assignee == null) {
                assignee = actor;
            }
        }
        if (target == WorkOrderStatus.COMPLETED) {
            completedAt = now;
            resolution = note.strip();
        }
        String message = from + " → " + target + (note == null || note.isBlank() ? "" : ": " + note.strip());
        log(ActivityType.STATUS_CHANGED, message, actor, now);
    }

    public void addPart(WorkOrderPart part, String actor, Instant now) {
        ensureCanUseParts();
        part.attachTo(this);
        parts.add(part);
        log(ActivityType.PART_USED, part.getQuantity() + " " + part.getUnit() + " × " + part.getPartNumber() + " "
                + part.getPartName(), actor, now);
    }

    public void removePart(WorkOrderPart part, String actor, Instant now) {
        ensureCanUseParts();
        parts.remove(part);
        log(ActivityType.PART_RETURNED, part.getQuantity() + " " + part.getUnit() + " × " + part.getPartNumber()
                + " returned to stock", actor, now);
    }

    public WorkOrderPart part(UUID lineId) {
        return parts.stream().filter(p -> p.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Part line", lineId));
    }

    /** Parts can be booked while the job is open, in progress or on hold, not once it is closed. */
    public void ensureCanUseParts() {
        ensureOpen("given parts");
    }

    public BigDecimal partsCost() {
        return parts.stream().map(WorkOrderPart::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isOverdue(LocalDate today) {
        return dueDate != null && dueDate.isBefore(today) && !status.isClosed();
    }

    private void ensureOpen(String action) {
        if (status.isClosed()) {
            throw new BusinessRuleException("A " + status.name().toLowerCase() + " work order cannot be " + action);
        }
    }

    private void log(ActivityType activity, String message, String actor, Instant now) {
        activities.add(new WorkOrderActivity(this, activity, message, actor, now));
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

    public Set<UUID> getAlertIds() {
        return alertIds;
    }

    public boolean answersAlerts() {
        return !alertIds.isEmpty();
    }

    public WorkOrderType getType() {
        return type;
    }

    public WorkOrderPriority getPriority() {
        return priority;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getAssignee() {
        return assignee;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getResolution() {
        return resolution;
    }

    public List<WorkOrderPart> getParts() {
        return parts;
    }

    public List<WorkOrderActivity> getActivities() {
        return activities;
    }
}
