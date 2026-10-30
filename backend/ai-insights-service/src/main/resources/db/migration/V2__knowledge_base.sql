-- Knowledge base of the maintenance copilot: machine manuals and procedures, cut into passages.
-- Passages are found by keywords (Postgres full-text search, always on) and by meaning (pgvector embeddings,
-- when an embedding model is configured); the copilot merges both rankings.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_documents (
    id              UUID PRIMARY KEY,
    -- builtin:<file name> for the manuals shipped with the service, upload:<id> for uploaded ones.
    source_key      VARCHAR(200)  NOT NULL UNIQUE,
    title           VARCHAR(200)  NOT NULL,
    kind            VARCHAR(15)   NOT NULL CHECK (kind IN ('MANUAL', 'PROCEDURE')),
    origin          VARCHAR(10)   NOT NULL CHECK (origin IN ('BUILT_IN', 'UPLOADED')),
    file_name       VARCHAR(255)  NOT NULL,
    -- Machine models the document is about (lower case, as in asset-service); empty = applies to every machine.
    machine_models  TEXT[]        NOT NULL DEFAULT '{}',
    content_hash    CHAR(64)      NOT NULL,
    uploaded_by     VARCHAR(100),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL
);

CREATE TABLE knowledge_chunks (
    id               UUID PRIMARY KEY,
    document_id      UUID          NOT NULL REFERENCES knowledge_documents (id) ON DELETE CASCADE,
    chunk_index      INTEGER       NOT NULL,
    section          VARCHAR(300),
    page             INTEGER,
    content          TEXT          NOT NULL,
    -- Words of the section title weigh more than words of the text.
    search_text      TSVECTOR      GENERATED ALWAYS AS (
                         setweight(to_tsvector('english', coalesce(section, '')), 'A')
                         || setweight(to_tsvector('english', content), 'B')) STORED,
    -- 768 dimensions: nomic-embed-text, the default Ollama embedding model. Null until embedded.
    embedding        VECTOR(768),
    -- Which model wrote the embedding: changing the model re-embeds every passage.
    embedding_model  VARCHAR(100),
    CONSTRAINT ux_knowledge_chunks_position UNIQUE (document_id, chunk_index)
);
CREATE INDEX ix_knowledge_chunks_search ON knowledge_chunks USING gin (search_text);
CREATE INDEX ix_knowledge_chunks_embedding ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);
