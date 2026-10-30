package com.factorymind.insights.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A manual or procedure of the knowledge base.
 *
 * @param machineModels lower-case machine models it applies to; empty = every machine
 * @param embeddedChunks passages that have an embedding of the current model (0 when embeddings are off)
 */
public record KnowledgeDocument(UUID id, String title, DocumentKind kind, Origin origin, String fileName,
                                List<String> machineModels, int chunks, int embeddedChunks, String uploadedBy,
                                Instant createdAt, Instant updatedAt) {

    public enum Origin {
        /** Shipped with the service (src/main/resources/knowledge), reloaded on start. */
        BUILT_IN,
        UPLOADED
    }

    /** One passage, for reading a document the way the copilot sees it. */
    public record Chunk(int index, String section, Integer page, String content) {
    }

    public record Detail(KnowledgeDocument document, List<Chunk> chunks) {
    }
}
