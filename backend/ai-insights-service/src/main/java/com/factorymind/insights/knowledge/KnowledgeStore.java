package com.factorymind.insights.knowledge;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL of the knowledge base. Plain JDBC rather than JPA: the interesting parts are Postgres full-text search
 * and pgvector distances, which read best as SQL.
 */
@Repository
public class KnowledgeStore {

    /** A document applies when it is about no machine in particular, or about the machine asked about. */
    private static final String SCOPE = """
            (cardinality(d.machine_models) = 0 OR CAST(:machine AS text) IS NULL
             OR lower(CAST(:machine AS text)) = ANY(d.machine_models))""";

    private static final String DOCUMENT_COLUMNS = """
            d.id, d.title, d.kind, d.origin, d.file_name, array_to_string(d.machine_models, ',') AS machines,
            d.uploaded_by, d.created_at, d.updated_at,
            (SELECT count(*) FROM knowledge_chunks c WHERE c.document_id = d.id) AS chunks,
            (SELECT count(*) FROM knowledge_chunks c WHERE c.document_id = d.id
                                                       AND c.embedding_model = :embeddingModel) AS embedded""";

    private final JdbcClient jdbc;

    public KnowledgeStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Existing(UUID id, String contentHash) {
    }

    /** A passage waiting for an embedding, with the title and section that give it context. */
    public record PendingChunk(UUID id, String text) {
    }

    public record Ranked(UUID chunkId, double value) {
    }

    public Optional<Existing> findBySourceKey(String sourceKey) {
        return jdbc.sql("SELECT id, content_hash FROM knowledge_documents WHERE source_key = :key")
                .param("key", sourceKey)
                .query((rs, n) -> new Existing(rs.getObject("id", UUID.class), rs.getString("content_hash")))
                .optional();
    }

    public List<String> builtInSourceKeys() {
        return jdbc.sql("SELECT source_key FROM knowledge_documents WHERE origin = 'BUILT_IN'")
                .query(String.class).list();
    }

    /** Inserts the document, or replaces the metadata and every passage of the one with the same source key. */
    public UUID save(String sourceKey, DocumentParser.Parsed parsed, KnowledgeDocument.Origin origin, String fileName,
                     String contentHash, String uploadedBy, List<Chunker.Chunk> chunks, Instant now) {
        UUID id = findBySourceKey(sourceKey).map(Existing::id).orElse(null);
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO knowledge_documents (id, source_key, title, kind, origin, file_name, machine_models,
                                                     content_hash, uploaded_by, created_at, updated_at)
                    VALUES (:id, :key, :title, :kind, :origin, :file, string_to_array(:machines, ','), :hash,
                            :uploadedBy, :now, :now)""")
                    .param("id", id).param("key", sourceKey).param("title", parsed.title())
                    .param("kind", parsed.kind().name()).param("origin", origin.name()).param("file", fileName)
                    .param("machines", String.join(",", parsed.machineModels())).param("hash", contentHash)
                    .param("uploadedBy", uploadedBy).param("now", timestamp(now))
                    .update();
        } else {
            jdbc.sql("""
                    UPDATE knowledge_documents SET title = :title, kind = :kind, file_name = :file,
                           machine_models = string_to_array(:machines, ','), content_hash = :hash, updated_at = :now
                    WHERE id = :id""")
                    .param("id", id).param("title", parsed.title()).param("kind", parsed.kind().name())
                    .param("file", fileName).param("machines", String.join(",", parsed.machineModels()))
                    .param("hash", contentHash).param("now", timestamp(now))
                    .update();
            jdbc.sql("DELETE FROM knowledge_chunks WHERE document_id = :id").param("id", id).update();
        }
        for (Chunker.Chunk chunk : chunks) {
            jdbc.sql("""
                    INSERT INTO knowledge_chunks (id, document_id, chunk_index, section, page, content)
                    VALUES (:id, :document, :index, :section, :page, :content)""")
                    .param("id", UUID.randomUUID()).param("document", id).param("index", chunk.index())
                    .param("section", truncate(chunk.section(), 300)).param("page", chunk.page())
                    .param("content", chunk.content())
                    .update();
        }
        return id;
    }

    public int delete(UUID id) {
        return jdbc.sql("DELETE FROM knowledge_documents WHERE id = :id").param("id", id).update();
    }

    public int deleteBySourceKey(String sourceKey) {
        return jdbc.sql("DELETE FROM knowledge_documents WHERE source_key = :key").param("key", sourceKey).update();
    }

    public List<KnowledgeDocument> documents(String embeddingModel) {
        return jdbc.sql("SELECT " + DOCUMENT_COLUMNS + " FROM knowledge_documents d ORDER BY d.kind, d.title")
                .param("embeddingModel", embeddingModel)
                .query(KnowledgeStore::document)
                .list();
    }

    public Optional<KnowledgeDocument> document(UUID id, String embeddingModel) {
        return jdbc.sql("SELECT " + DOCUMENT_COLUMNS + " FROM knowledge_documents d WHERE d.id = :id")
                .param("id", id).param("embeddingModel", embeddingModel)
                .query(KnowledgeStore::document)
                .optional();
    }

    public List<KnowledgeDocument.Chunk> chunks(UUID documentId) {
        return jdbc.sql("""
                SELECT chunk_index, section, page, content FROM knowledge_chunks
                WHERE document_id = :id ORDER BY chunk_index""")
                .param("id", documentId)
                .query((rs, n) -> new KnowledgeDocument.Chunk(rs.getInt("chunk_index"), rs.getString("section"),
                        rs.getObject("page", Integer.class), rs.getString("content")))
                .list();
    }

    /**
     * Full-text search. Any word of the question may match (OR), so a long question still finds passages;
     * ts_rank_cd puts passages that hold more of the words, closer together, first. Section titles weigh more.
     */
    public List<Ranked> keywordSearch(String text, String machineModel, int limit) {
        return jdbc.sql("""
                SELECT c.id, ts_rank_cd(c.search_text, q.query) AS rank
                FROM knowledge_chunks c
                JOIN knowledge_documents d ON d.id = c.document_id
                CROSS JOIN LATERAL (SELECT replace(plainto_tsquery('english', :text)::text, ' & ', ' | ')::tsquery
                                    AS query) q
                WHERE c.search_text @@ q.query AND """ + SCOPE + """

                ORDER BY rank DESC, c.id
                LIMIT :limit""")
                .param("text", text).param("machine", machineModel).param("limit", limit)
                .query((rs, n) -> new Ranked(rs.getObject("id", UUID.class), rs.getDouble("rank")))
                .list();
    }

    /** Nearest passages by cosine distance (HNSW index), among those embedded by the current model. */
    public List<Ranked> vectorSearch(float[] query, String embeddingModel, String machineModel, int limit) {
        return jdbc.sql("""
                SELECT c.id, c.embedding <=> CAST(:vector AS vector) AS distance
                FROM knowledge_chunks c
                JOIN knowledge_documents d ON d.id = c.document_id
                WHERE c.embedding_model = :embeddingModel AND """ + SCOPE + """

                ORDER BY distance
                LIMIT :limit""")
                .param("vector", KnowledgeEmbeddings.literal(query)).param("embeddingModel", embeddingModel)
                .param("machine", machineModel).param("limit", limit)
                .query((rs, n) -> new Ranked(rs.getObject("id", UUID.class), rs.getDouble("distance")))
                .list();
    }

    /** The passages with these ids, with their document; the caller orders them. */
    public List<Passage> passages(Collection<UUID> chunkIds) {
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT c.id, c.document_id, d.title, d.kind, c.section, c.page, c.content
                FROM knowledge_chunks c JOIN knowledge_documents d ON d.id = c.document_id
                WHERE c.id IN (:ids)""")
                .param("ids", chunkIds)
                .query((rs, n) -> new Passage(rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
                        rs.getString("title"), DocumentKind.valueOf(rs.getString("kind")), rs.getString("section"),
                        rs.getObject("page", Integer.class), rs.getString("content"), 0, null, null))
                .list();
    }

    public List<PendingChunk> withoutEmbedding(String embeddingModel, int limit) {
        return jdbc.sql("""
                SELECT c.id, d.title, c.section, c.content
                FROM knowledge_chunks c JOIN knowledge_documents d ON d.id = c.document_id
                WHERE c.embedding_model IS DISTINCT FROM :embeddingModel
                ORDER BY d.created_at, d.id, c.chunk_index
                LIMIT :limit""")
                .param("embeddingModel", embeddingModel).param("limit", limit)
                .query((rs, n) -> new PendingChunk(rs.getObject("id", UUID.class), embeddingText(
                        rs.getString("title"), rs.getString("section"), rs.getString("content"))))
                .list();
    }

    public void saveEmbedding(UUID chunkId, float[] embedding, String embeddingModel) {
        jdbc.sql("UPDATE knowledge_chunks SET embedding = CAST(:vector AS vector), embedding_model = :model WHERE id = :id")
                .param("vector", KnowledgeEmbeddings.literal(embedding)).param("model", embeddingModel)
                .param("id", chunkId)
                .update();
    }

    public record Counts(long documents, long chunks, long embeddedChunks) {
    }

    public Counts counts(String embeddingModel) {
        return jdbc.sql("""
                SELECT (SELECT count(*) FROM knowledge_documents) AS documents,
                       count(*) AS chunks,
                       count(*) FILTER (WHERE embedding_model = :embeddingModel) AS embedded
                FROM knowledge_chunks""")
                .param("embeddingModel", embeddingModel)
                .query((rs, n) -> new Counts(rs.getLong("documents"), rs.getLong("chunks"), rs.getLong("embedded")))
                .single();
    }

    /** The passage is embedded with its title and section, so "Troubleshooting" passages know which machine. */
    static String embeddingText(String title, String section, String content) {
        return title + (section == null ? "" : " — " + section) + "\n\n" + content;
    }

    private static KnowledgeDocument document(ResultSet rs, int row) throws SQLException {
        String machines = rs.getString("machines");
        return new KnowledgeDocument(rs.getObject("id", UUID.class), rs.getString("title"),
                DocumentKind.valueOf(rs.getString("kind")), KnowledgeDocument.Origin.valueOf(rs.getString("origin")),
                rs.getString("file_name"),
                machines == null || machines.isEmpty() ? List.of() : Arrays.asList(machines.split(",")),
                rs.getInt("chunks"), rs.getInt("embedded"), rs.getString("uploaded_by"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
