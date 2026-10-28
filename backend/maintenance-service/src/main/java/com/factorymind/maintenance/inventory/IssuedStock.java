package com.factorymind.maintenance.inventory;

import java.math.BigDecimal;
import java.util.UUID;

/** The part of inventory-service's stock movement response that a work order keeps. */
public record IssuedStock(UUID partId, String partNumber, String partName, String unit, int quantity,
                          BigDecimal unitCost, int quantityAfter) {
}
