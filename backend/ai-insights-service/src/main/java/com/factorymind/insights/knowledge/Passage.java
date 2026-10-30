package com.factorymind.insights.knowledge;

import java.util.UUID;

/**
 * A passage found for a question.
 *
 * @param score fused score (reciprocal rank fusion); only the order matters
 * @param keywordRank rank in the full-text search, null when the words did not match
 * @param vectorRank rank in the embedding search, null when not searched or not close enough
 */
public record Passage(UUID chunkId, UUID documentId, String documentTitle, DocumentKind kind, String section,
                      Integer page, String content, double score, Integer keywordRank, Integer vectorRank) {

    /** "Schuler HP-400 hydraulic press › Troubleshooting", "Lockout/tagout (page 2)". */
    public String location() {
        String where = section == null ? documentTitle : documentTitle + " › " + section;
        return page == null || (section != null && section.startsWith("Page ")) ? where : where + " (page " + page + ")";
    }
}
