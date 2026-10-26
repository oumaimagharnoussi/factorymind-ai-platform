package com.factorymind.asset.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public final class AssetDtos {

    private AssetDtos() {
    }

    public record CreateAssetRequest(
            @NotBlank @Size(max = 40)
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]*", message = "letters, digits and dashes only")
            String assetCode,
            @NotBlank @Size(max = 120) String name,
            @NotNull UUID lineId,
            @Size(max = 120) String manufacturer,
            @Size(max = 120) String model,
            @NotNull Criticality criticality,
            @PastOrPresent LocalDate installedAt) {
    }

    /** The asset code is the machine's identity in telemetry and work orders, so it cannot be edited. */
    public record UpdateAssetRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull UUID lineId,
            @Size(max = 120) String manufacturer,
            @Size(max = 120) String model,
            @NotNull Criticality criticality,
            @PastOrPresent LocalDate installedAt) {
    }

    public record StatusChangeRequest(@NotNull AssetStatus status) {
    }

    public record AssetResponse(
            UUID id,
            String assetCode,
            String name,
            UUID lineId,
            String lineName,
            UUID factoryId,
            String factoryName,
            String manufacturer,
            String model,
            Criticality criticality,
            LocalDate installedAt,
            AssetStatus status,
            Instant updatedAt,
            long version) {
    }

    public record AssetSummary(long total, Map<AssetStatus, Long> byStatus, Map<Criticality, Long> byCriticality) {
    }
}
