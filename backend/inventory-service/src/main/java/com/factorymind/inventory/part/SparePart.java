package com.factorymind.inventory.part;

import com.factorymind.inventory.common.BaseEntity;
import com.factorymind.inventory.common.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A spare part kept in the plant store. The quantity on hand only changes through stock movements
 * ({@code StockService}), so every unit can be traced to a delivery, a work order or a stock count.
 */
@Entity
@Table(name = "spare_parts")
public class SparePart extends BaseEntity {

    /** Manufacturer or internal reference; unique, upper case. */
    @Column(name = "part_number", nullable = false, unique = true, length = 40)
    private String partNumber;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PartCategory category;

    /** pcs, L, m, set... */
    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    /** Reorder point: at or below it the part shows as low stock. */
    @Column(name = "minimum_stock", nullable = false)
    private int minimumStock;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    /** Shelf in the store, e.g. A-01-03. */
    @Column(length = 40)
    private String location;

    protected SparePart() {
    }

    public SparePart(String partNumber, String name, String description, PartCategory category, String unit,
                     int minimumStock, BigDecimal unitCost, String location) {
        this.partNumber = partNumber;
        this.quantityOnHand = 0;
        update(name, description, category, unit, minimumStock, unitCost, location);
    }

    public void update(String name, String description, PartCategory category, String unit, int minimumStock,
                       BigDecimal unitCost, String location) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.unit = unit;
        this.minimumStock = minimumStock;
        this.unitCost = unitCost;
        this.location = location;
    }

    public void receive(int quantity, BigDecimal newUnitCost) {
        quantityOnHand += quantity;
        if (newUnitCost != null) {
            unitCost = newUnitCost;
        }
    }

    public void issue(int quantity) {
        if (quantity > quantityOnHand) {
            throw new BusinessRuleException("Only " + quantityOnHand + " " + unit + " of " + partNumber
                    + " in stock, " + quantity + " requested");
        }
        quantityOnHand -= quantity;
    }

    public void giveBack(int quantity) {
        quantityOnHand += quantity;
    }

    /** Sets the quantity found during a stock count; returns the difference with what the system had. */
    public int adjustTo(int counted) {
        int change = counted - quantityOnHand;
        quantityOnHand = counted;
        return change;
    }

    public boolean isLowStock() {
        return quantityOnHand <= minimumStock;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public PartCategory getCategory() {
        return category;
    }

    public String getUnit() {
        return unit;
    }

    public int getQuantityOnHand() {
        return quantityOnHand;
    }

    public int getMinimumStock() {
        return minimumStock;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getLocation() {
        return location;
    }
}
