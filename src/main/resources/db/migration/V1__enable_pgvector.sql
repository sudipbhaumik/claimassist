-- Stage 0: Enable the pgvector extension.
-- The vector_store table schema is managed in Stage 1 (V2 migration).
CREATE EXTENSION IF NOT EXISTS vector;
