package com.factorymind.maintenance.workorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One line of a work order's history: who did what, and when. Never changed after it is written. */
@Entity
@Table(name = "work_order_activities")
public class WorkOrderActivity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id")
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityType type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false, length = 100)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected WorkOrderActivity() {
    }

    WorkOrderActivity(WorkOrder workOrder, ActivityType type, String message, String actor, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.workOrder = workOrder;
        this.type = type;
        this.message = message;
        this.actor = actor;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public ActivityType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
