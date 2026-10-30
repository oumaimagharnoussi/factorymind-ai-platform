package com.factorymind.insights.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * A stand-in for nomic-embed-text in tests: each word adds 1 to one of 768 buckets, then the vector is
 * normalised. Texts sharing words are close, which is enough to test the pgvector path without Ollama.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        for (String text : request.getInstructions()) {
            embeddings.add(new Embedding(vector(text), embeddings.size()));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    static float[] vector(String text) {
        float[] vector = new float[KnowledgeEmbeddings.DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.length() > 2 && !word.equals("search_query") && !word.equals("search_document")) {
                vector[Math.floorMod(word.hashCode(), vector.length)] += 1;
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= (float) Math.sqrt(norm);
            }
        }
        return vector;
    }
}
