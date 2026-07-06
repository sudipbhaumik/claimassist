-- HNSW index for fast approximate cosine similarity search on embedded chunks.
-- Added in increment 1.2 once real vectors are being populated on every ingest.
-- Spec note: SPEC-1.2 calls this V3; V3 is already used by create_claim_notes, so this is V4.
CREATE INDEX IF NOT EXISTS idx_chunks_embedding
    ON document_chunks USING hnsw (embedding vector_cosine_ops);
