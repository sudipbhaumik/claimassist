package com.claimassist.store;

import com.claimassist.chunking.TextChunk;
import com.claimassist.common.error.ClaimAssistException;
import com.claimassist.common.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persists {@link TextChunk} rows to {@code document_chunks}.
 *
 * <p>Uses {@code ON CONFLICT (content_hash) DO NOTHING} so ingest is idempotent. The affected-row
 * count from {@link #upsert} drives the {@code chunksCreated} / {@code chunksSkipped} counters in
 * {@code IngestionResult}.
 *
 * <p>The {@code metadata} column is {@code jsonb}; the {@code ?::jsonb} cast in the SQL lets us
 * pass a JSON string without importing the PostgreSQL PGobject type into main code.
 *
 * <p>In increment 1.1 the {@code embedding} column is left null (populated in 1.2).
 */
@Repository
public class DocumentChunkRepository {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public DocumentChunkRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  /**
   * Inserts a chunk row.
   *
   * @return 1 if the row was inserted, 0 if skipped (duplicate {@code content_hash}).
   */
  public int upsert(TextChunk chunk) {
    try {
      String metaJson = objectMapper.writeValueAsString(chunk.metadata());
      return jdbc.update(
          """
          INSERT INTO document_chunks (content, metadata, content_hash)
          VALUES (?, ?::jsonb, ?)
          ON CONFLICT (content_hash) DO NOTHING
          """,
          chunk.text(),
          metaJson,
          chunk.contentHash());
    } catch (JsonProcessingException e) {
      throw new ClaimAssistException(
          ErrorCode.INTERNAL_ERROR, "Failed to serialize chunk metadata", e);
    }
  }
}
