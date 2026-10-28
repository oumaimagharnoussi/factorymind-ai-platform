package com.factorymind.insights.assistant;

import java.time.Instant;
import java.util.UUID;

/**
 * @param text        Markdown
 * @param generatedBy the language model that wrote it ({@code anthropic:claude-sonnet-5-5}), or {@code rules}
 */
public record MachineReport(UUID assetId, String machine, String text, String generatedBy, Instant generatedAt) {
}
