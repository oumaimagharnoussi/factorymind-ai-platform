package com.factorymind.asset.factory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class FactoryDtos {

    private FactoryDtos() {
    }

    public record FactoryRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 200) String location,
            @NotBlank @Size(max = 60) String timezone) {
    }

    public record FactoryResponse(
            UUID id, String name, String location, String timezone, long lineCount, Instant createdAt) {
    }
}
