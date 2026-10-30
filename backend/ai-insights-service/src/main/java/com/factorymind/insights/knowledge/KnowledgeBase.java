package com.factorymind.insights.knowledge;

import com.factorymind.insights.common.ConflictException;
import com.factorymind.insights.common.NotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manuals and procedures the copilot answers from. Retrieval is hybrid: Postgres full-text search finds exact
 * terms (error codes, part numbers, "E-217"), embeddings find the same meaning in other words ("the press runs
 * hot" for "oil overheating"). The two rankings are merged with reciprocal rank fusion, which needs no tuning
 * because it only looks at ranks, not at the incomparable scores.
 */
@Service
public class KnowledgeBase {

    /** The usual RRF constant: a passage ranked 1st scores 1/61, 10th 1/70; being in both lists matters most. */
    static final int RRF_K = 60;
    /** Candidates taken from each search before fusion. */
    static final int CANDIDATES = 20;

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);

    private final KnowledgeStore store;
    private final KnowledgeEmbeddings embeddings;
    private final Chunker chunker;
    private final Clock clock;

    public KnowledgeBase(KnowledgeStore store, KnowledgeEmbeddings embeddings,
                         @Value("${insights.knowledge.chunk-size:1000}") int chunkSize, Clock clock) {
        this.store = store;
        this.embeddings = embeddings;
        this.chunker = new Chunker(chunkSize);
        this.clock = clock;
    }

    public record SearchResult(List<Passage> passages, Retrieval retrieval) {
    }

    public enum Retrieval {
        /** Keywords and embeddings. */
        HYBRID,
        /** Keywords only: no embedding model, or it did not answer. */
        KEYWORD
    }

    public record Status(Retrieval retrieval, String embeddingModel, long documents, long chunks, long embeddedChunks) {
    }

    /** Saves the document (or replaces the one with the same source key) and cuts it into passages. */
    @Transactional
    public UUID save(String sourceKey, DocumentParser.Parsed parsed, KnowledgeDocument.Origin origin, String fileName,
                     byte[] content, String uploadedBy) {
        List<Chunker.Chunk> chunks = chunker.chunk(parsed.sections());
        UUID id = store.save(sourceKey, parsed, origin, fileName, sha256(content), uploadedBy, chunks, clock.instant());
        log.info("Knowledge base: {} \"{}\" saved as {} passages", origin, parsed.title(), chunks.size());
        return id;
    }

    /** Whether the stored copy of this source differs from {@code content} (or does not exist). */
    public boolean changed(String sourceKey, byte[] content) {
        return store.findBySourceKey(sourceKey).map(e -> !e.contentHash().equals(sha256(content))).orElse(true);
    }

    @Transactional
    public void delete(UUID id) {
        KnowledgeDocument document = store.document(id, null).orElseThrow(() -> new NotFoundException("Document", id));
        if (document.origin() == KnowledgeDocument.Origin.BUILT_IN) {
            throw new ConflictException("Built-in manuals come back on every start; change them in the source code.");
        }
        store.delete(id);
    }

    public List<KnowledgeDocument> documents() {
        return store.documents(embeddings.name().orElse(null));
    }

    public KnowledgeDocument.Detail document(UUID id) {
        KnowledgeDocument document = store.document(id, embeddings.name().orElse(null))
                .orElseThrow(() -> new NotFoundException("Document", id));
        return new KnowledgeDocument.Detail(document, store.chunks(id));
    }

    public Status status() {
        String model = embeddings.name().orElse(null);
        KnowledgeStore.Counts counts = store.counts(model);
        return new Status(model == null ? Retrieval.KEYWORD : Retrieval.HYBRID, model, counts.documents(),
                counts.chunks(), counts.embeddedChunks());
    }

    /**
     * The passages that best answer {@code question}.
     *
     * @param machineModel when set, only general procedures and the manuals of this model are searched
     */
    public SearchResult search(String question, String machineModel, int limit) {
        List<KnowledgeStore.Ranked> keyword = store.keywordSearch(question, machineModel, CANDIDATES);
        List<KnowledgeStore.Ranked> vector = List.of();
        Retrieval retrieval = Retrieval.KEYWORD;
        if (embeddings.enabled()) {
            try {
                vector = store.vectorSearch(embeddings.embedQuery(question), embeddings.name().orElseThrow(),
                        machineModel, CANDIDATES);
                retrieval = Retrieval.HYBRID;
            } catch (RuntimeException e) {
                log.warn("Embedding search unavailable, using keywords only: {}", e.getMessage());
            }
        }
        List<UUID> keywordIds = keyword.stream().map(KnowledgeStore.Ranked::chunkId).toList();
        List<UUID> vectorIds = vector.stream().map(KnowledgeStore.Ranked::chunkId).toList();
        Map<UUID, Double> fused = fuse(List.of(keywordIds, vectorIds));

        List<UUID> best = fused.keySet().stream().limit(limit).toList();
        Map<UUID, Passage> byId = store.passages(best).stream()
                .collect(Collectors.toMap(Passage::chunkId, Function.identity()));
        List<Passage> passages = new ArrayList<>();
        for (UUID id : best) {
            Passage p = byId.get(id);
            if (p != null) {
                passages.add(new Passage(p.chunkId(), p.documentId(), p.documentTitle(), p.kind(), p.section(),
                        p.page(), p.content(), fused.get(id), rank(keywordIds, id), rank(vectorIds, id)));
            }
        }
        return new SearchResult(passages, retrieval);
    }

    /**
     * Reciprocal rank fusion: each list adds 1 / (k + rank) to the passages it holds. Highest first; ties keep
     * the order in which passages were first seen (keywords before vectors).
     */
    static Map<UUID, Double> fuse(List<List<UUID>> rankings) {
        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, Integer> firstSeen = new HashMap<>();
        for (List<UUID> ranking : rankings) {
            for (int i = 0; i < ranking.size(); i++) {
                scores.merge(ranking.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
                firstSeen.putIfAbsent(ranking.get(i), firstSeen.size());
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()
                        .thenComparing(e -> firstSeen.get(e.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Embeds up to {@code max} passages that have no embedding of the current model (new documents, or every
     * passage after a change of model). Returns how many were embedded.
     */
    public int embedPending(int max) {
        if (!embeddings.enabled()) {
            return 0;
        }
        String model = embeddings.name().orElseThrow();
        int done = 0;
        while (done < max) {
            List<KnowledgeStore.PendingChunk> batch = store.withoutEmbedding(model, Math.min(16, max - done));
            if (batch.isEmpty()) {
                break;
            }
            List<float[]> vectors = embeddings.embedDocuments(batch.stream().map(KnowledgeStore.PendingChunk::text).toList());
            for (int i = 0; i < batch.size(); i++) {
                store.saveEmbedding(batch.get(i).id(), vectors.get(i), model);
            }
            done += batch.size();
        }
        return done;
    }

    private static Integer rank(List<UUID> ranking, UUID id) {
        int index = ranking.indexOf(id);
        return index < 0 ? null : index + 1;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
