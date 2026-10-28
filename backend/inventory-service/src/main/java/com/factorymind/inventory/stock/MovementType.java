package com.factorymind.inventory.stock;

public enum MovementType {
    /** Delivery from a supplier. */
    RECEIPT,
    /** Taken out for a work order. */
    ISSUE,
    /** Brought back unused from a work order. */
    RETURN,
    /** Correction after a physical stock count. */
    ADJUSTMENT
}
