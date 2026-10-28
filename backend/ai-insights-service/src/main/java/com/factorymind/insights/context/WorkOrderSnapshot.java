package com.factorymind.insights.context;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The latest known state of a work order, copied from maintenance-service's events for the assistant's context. */
@Entity
@Table(name = "work_order_snapshots")
public class WorkOrderSnapshot {

    @Id
    private UUID workOrderId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false)
    private UUID assetId;

    @Column(nullable = false, length = 15)
    private String type;

    @Column(nullable = false, length = 10)
    private String priority;

    @Column(nullable = false, length = 15)
    private String status;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 100)
    private String assignee;

    @Column(nullable = false)
    private Instant updatedAt;

    protected WorkOrderSnapshot() {
    }

    WorkOrderSnapshot(UUID workOrderId) {
        this.workOrderId = workOrderId;
    }

    boolean apply(WorkOrderEventMessage event) {
        if (updatedAt != null && event.occurredAt().isBefore(updatedAt)) {
            return false;
        }
        code = event.code();
        assetId = event.assetId();
        type = event.type();
        priority = event.priority();
        status = event.status();
        title = event.title();
        assignee = event.assignee();
        updatedAt = event.occurredAt();
        return true;
    }

    public boolean isOpen() {
        return !"COMPLETED".equals(status) && !"CANCELLED".equals(status);
    }

    public UUID getWorkOrderId() {
        return workOrderId;
    }

    public String getCode() {
        return code;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getType() {
        return type;
    }

    public String getPriority() {
        return priority;
    }

    public String getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getAssignee() {
        return assignee;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
