package com.factorymind.maintenance.workorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A spare part taken out of stock for this work order. Part number, name and cost are copied from
 * inventory-service at that moment, so the work order keeps its real cost even if the catalogue changes.
 * The id is also the idempotency key of the stock movement in inventory-service.
 */
@Entity
@Table(name = "work_order_parts")
public class WorkOrderPart {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id")
    private WorkOrder workOrder;

    @Column(name = "part_id", nullable = false)
    private UUID partId;

    @Column(name = "part_number", nullable = false, length = 40)
    private String partNumber;

    @Column(name = "part_name", nullable = false, length = 120)
    private String partName;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "issued_by", nullable = false, length = 100)
    private String issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    protected WorkOrderPart() {
    }

    public WorkOrderPart(UUID id, UUID partId, String partNumber, String partName, String unit, int quantity,
                         BigDecimal unitCost, String issuedBy, Instant issuedAt) {
        this.id = id;
        this.partId = partId;
        this.partNumber = partNumber;
        this.partName = partName;
        this.unit = unit;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.issuedBy = issuedBy;
        this.issuedAt = issuedAt;
    }

    void attachTo(WorkOrder workOrder) {
        this.workOrder = workOrder;
    }

    public BigDecimal cost() {
        return unitCost.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getId() {
        return id;
    }

    public UUID getPartId() {
        return partId;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public String getPartName() {
        return partName;
    }

    public String getUnit() {
        return unit;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
