-- document_chunks: stores parsed, normalized, chunked text from claim documents.
-- embedding column is nullable in 1.1 (populated in increment 1.2 when embedding pipeline is built).
-- content_hash enforces idempotent ingest — re-posting the same chunk is silently ignored.
-- metadata is jsonb so Tier 1 + Tier 2 fields are queryable without schema changes.
CREATE TABLE IF NOT EXISTS document_chunks (
    id           uuid        DEFAULT gen_random_uuid() PRIMARY KEY,
    content      text        NOT NULL,
    metadata     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    embedding    vector(768),
    content_hash text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- GIN index for jsonb equality / containment queries (claim_id, doc_type, etc.)
CREATE INDEX IF NOT EXISTS idx_chunks_metadata ON document_chunks USING gin (metadata);

-- Unique index that drives ON CONFLICT DO NOTHING dedup
CREATE UNIQUE INDEX IF NOT EXISTS uq_chunks_hash ON document_chunks (content_hash);

-- HNSW vector similarity index deferred to 1.2 (index requires non-null vectors)
