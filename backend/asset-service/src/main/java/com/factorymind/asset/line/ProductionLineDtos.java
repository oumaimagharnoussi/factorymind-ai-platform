package com.factorymind.asset.line;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ProductionLineDtos {

    private ProductionLineDtos() {
    }

    public record ProductionLineRequest(
            @NotNull UUID factoryId,
            @NotBlank @Size(max = 120) String name,
            @NotNull LineStatus status) {
    }

    public record ProductionLineResponse(
            UUID id, UUID factoryId, String factoryName, String name, LineStatus status, long assetCount) {
    }
}
