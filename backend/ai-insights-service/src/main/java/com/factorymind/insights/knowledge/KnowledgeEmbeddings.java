package com.factorymind.insights.knowledge;

import java.util.List;
import java.util.Optional;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The embedding model chosen with {@code spring.ai.model.embedding}: {@code ollama} (nomic-embed-text, local
 * and free) or {@code none}. Anthropic has no embedding model, so with Claude as the chat model the embeddings
 * still come from Ollama. With none, passages are found by keywords only.
 */
@Component
public class KnowledgeEmbeddings {

    /** The width of the embedding column (nomic-embed-text). */
    public static final int DIMENSIONS = 768;

    private final EmbeddingModel model;
    private final String name;
    private final String queryPrefix;
    private final String documentPrefix;

    public KnowledgeEmbeddings(ObjectProvider<EmbeddingModel> embeddingModel, Environment environment,
                               @Value("${insights.knowledge.query-prefix:}") String queryPrefix,
                               @Value("${insights.knowledge.document-prefix:}") String documentPrefix) {
        String provider = environment.getProperty("spring.ai.model.embedding", "none");
        this.model = embeddingModel.getIfAvailable();
        this.name = model == null ? null
                : provider + ":" + environment.getProperty("spring.ai." + provider + ".embedding.model", "default");
        this.queryPrefix = queryPrefix;
        this.documentPrefix = documentPrefix;
    }

    public boolean enabled() {
        return model != null;
    }

    /** For example {@code ollama:nomic-embed-text}; stored next to each embedding. Empty when off. */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /** nomic-embed-text is trained with "search_query: " and "search_document: " in front of the text. */
    public float[] embedQuery(String text) {
        return check(model.embed(queryPrefix + text));
    }

    public List<float[]> embedDocuments(List<String> texts) {
        return model.embed(texts.stream().map(t -> documentPrefix + t).toList()).stream()
                .map(this::check)
                .toList();
    }

    private float[] check(float[] vector) {
        if (vector.length != DIMENSIONS) {
            throw new IllegalStateException("The embedding model " + name + " returns " + vector.length
                    + " dimensions; the knowledge base stores " + DIMENSIONS + " (nomic-embed-text).");
        }
        return vector;
    }

    /** pgvector's text form, "[0.1,0.2,...]", cast to vector in SQL. */
    static String literal(float[] vector) {
        StringBuilder text = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }
}
