package com.factorymind.inventory.stock;

import com.factorymind.inventory.part.SparePart;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One change of a part's quantity on hand. Never updated or deleted: the stock ledger. */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id")
    private SparePart part;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MovementType type;

    /** Signed: positive into the store, negative out of it. */
    @Column(name = "quantity_change", nullable = false)
    private int change;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    /** Idempotency key sent by the caller (maintenance-service sends one per part line). */
    @Column(name = "request_id", unique = true)
    private UUID requestId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Column(name = "work_order_code", length = 20)
    private String workOrderCode;

    /** Supplier delivery note, purchase order... */
    @Column(length = 60)
    private String reference;

    @Column(length = 500)
    private String note;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    protected StockMovement() {
    }

    public StockMovement(SparePart part, MovementType type, int change, UUID requestId, UUID workOrderId,
                         String workOrderCode, String reference, String note, String performedBy, Instant performedAt) {
        this.id = UUID.randomUUID();
        this.part = part;
        this.type = type;
        this.change = change;
        this.quantityAfter = part.getQuantityOnHand();
        this.unitCost = part.getUnitCost();
        this.requestId = requestId;
        this.workOrderId = workOrderId;
        this.workOrderCode = workOrderCode;
        this.reference = reference;
        this.note = note;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
    }

    public UUID getId() {
        return id;
    }

    public SparePart getPart() {
        return part;
    }

    public MovementType getType() {
        return type;
    }

    public int getChange() {
        return change;
    }

    public int getQuantityAfter() {
        return quantityAfter;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getWorkOrderId() {
        return workOrderId;
    }

    public String getWorkOrderCode() {
        return workOrderCode;
    }

    public String getReference() {
        return reference;
    }

    public String getNote() {
        return note;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }
}
