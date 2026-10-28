package com.factorymind.inventory.stock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class StockDtos {

    private StockDtos() {
    }

    /** @param unitCost price on this delivery; when given it becomes the part's unit cost */
    public record ReceiptRequest(
            @Min(1) @Max(100_000) int quantity,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitCost,
            @Size(max = 60) String reference,
            @Size(max = 500) String note) {
    }

    /** Issue to, or return from, a work order. {@code requestId} makes a retried call harmless. */
    public record WorkOrderMovementRequest(
            @Min(1) @Max(10_000) int quantity,
            @NotNull UUID requestId,
            @NotNull UUID workOrderId,
            @Size(max = 20) String workOrderCode) {
    }

    /** @param countedQuantity what is physically on the shelf */
    public record AdjustmentRequest(@Min(0) @Max(1_000_000) int countedQuantity, @NotBlank @Size(max = 500) String note) {
    }

    /**
     * @param quantity size of the movement (always positive)
     * @param change   effect on the stock (negative for issues)
     */
    public record MovementResponse(
            UUID id,
            UUID partId,
            String partNumber,
            String partName,
            String unit,
            MovementType type,
            int quantity,
            int change,
            int quantityAfter,
            BigDecimal unitCost,
            UUID workOrderId,
            String workOrderCode,
            String reference,
            String note,
            String performedBy,
            Instant performedAt) {

        static MovementResponse of(StockMovement m) {
            var part = m.getPart();
            return new MovementResponse(m.getId(), part.getId(), part.getPartNumber(), part.getName(), part.getUnit(),
                    m.getType(), Math.abs(m.getChange()), m.getChange(), m.getQuantityAfter(), m.getUnitCost(),
                    m.getWorkOrderId(), m.getWorkOrderCode(), m.getReference(), m.getNote(), m.getPerformedBy(),
                    m.getPerformedAt());
        }
    }
}
