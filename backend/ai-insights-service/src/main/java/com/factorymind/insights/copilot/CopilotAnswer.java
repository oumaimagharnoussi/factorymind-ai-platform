package com.factorymind.insights.copilot;

import com.factorymind.insights.knowledge.DocumentKind;
import com.factorymind.insights.knowledge.KnowledgeBase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param answer Markdown citing sources as [n]
 * @param generatedBy "anthropic:claude-sonnet-5-5", or "search" when no language model wrote the answer
 * @param machine the machine the question was about, when one was selected or named ("M-102 Hydraulic Press")
 * @param notice why the answer is passages only, when a language model is configured but failed
 */
public record CopilotAnswer(String answer, Mode mode, String generatedBy, UUID assetId, String machine,
                            KnowledgeBase.Retrieval retrieval, List<Source> sources, String notice,
                            Instant answeredAt) {

    public enum Mode {
        /** Written by the language model from the passages and live plant data. */
        GENERATED,
        /** No language model: the passages that match best, for the reader to read. */
        PASSAGES_ONLY
    }

    /**
     * A passage the copilot read.
     *
     * @param cited whether the answer refers to it as [number]
     * @param keywordRank rank in the keyword search, null when its words did not match
     * @param vectorRank rank in the embedding search, null when not found by meaning
     */
    public record Source(int number, UUID documentId, String documentTitle, DocumentKind kind, String section,
                         Integer page, String excerpt, boolean cited, Integer keywordRank, Integer vectorRank) {
    }
}
