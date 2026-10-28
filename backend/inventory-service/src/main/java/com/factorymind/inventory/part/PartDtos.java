package com.factorymind.inventory.part;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PartDtos {

    private PartDtos() {
    }

    /** A new part starts with no stock; its first delivery is a receipt. */
    public record CreatePartRequest(
            @NotBlank @Size(max = 40)
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9./-]*", message = "letters, digits, dots, slashes and dashes only")
            String partNumber,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull PartCategory category,
            @NotBlank @Size(max = 20) String unit,
            @Min(0) int minimumStock,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitCost,
            @Size(max = 40) String location) {
    }

    /** The part number identifies the part on work orders, so it cannot be edited. */
    public record UpdatePartRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull PartCategory category,
            @NotBlank @Size(max = 20) String unit,
            @Min(0) int minimumStock,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitCost,
            @Size(max = 40) String location) {
    }

    public record PartResponse(
            UUID id,
            String partNumber,
            String name,
            String description,
            PartCategory category,
            String unit,
            int quantityOnHand,
            int minimumStock,
            boolean lowStock,
            BigDecimal unitCost,
            BigDecimal stockValue,
            String location,
            Instant updatedAt,
            long version) {

        public static PartResponse of(SparePart p) {
            return new PartResponse(p.getId(), p.getPartNumber(), p.getName(), p.getDescription(), p.getCategory(),
                    p.getUnit(), p.getQuantityOnHand(), p.getMinimumStock(), p.isLowStock(), p.getUnitCost(),
                    p.getUnitCost().multiply(BigDecimal.valueOf(p.getQuantityOnHand())), p.getLocation(),
                    p.getUpdatedAt(), p.getVersion());
        }
    }

    /**
     * @param lowStock   parts at or below their minimum (out-of-stock ones included)
     * @param stockValue quantity on hand × unit cost, over all parts
     */
    public record InventorySummary(long parts, long lowStock, long outOfStock, BigDecimal stockValue) {
    }
}
