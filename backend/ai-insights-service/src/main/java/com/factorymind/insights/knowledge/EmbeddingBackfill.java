package com.factorymind.insights.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Embeds passages in the background: right after start, then every minute for new documents, for a change of
 * embedding model, or once Ollama is up (it may start after this service, or still be pulling its model).
 * Until then the copilot searches by keywords.
 */
@Component
public class EmbeddingBackfill {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfill.class);

    private final KnowledgeBase knowledge;
    private boolean failing;

    public EmbeddingBackfill(KnowledgeBase knowledge) {
        this.knowledge = knowledge;
    }

    @Scheduled(initialDelayString = "${insights.knowledge.backfill-delay:PT5S}",
            fixedDelayString = "${insights.knowledge.backfill-interval:PT1M}")
    public void run() {
        try {
            int embedded = knowledge.embedPending(2_000);
            if (embedded > 0) {
                log.info("Knowledge base: embedded {} passages", embedded);
            }
            failing = false;
        } catch (RuntimeException e) {
            // Logged once per outage, not every minute.
            if (!failing) {
                log.warn("Embedding model unavailable, the copilot searches by keywords until it answers: {}",
                        e.getMessage());
            }
            failing = true;
        }
    }
}
