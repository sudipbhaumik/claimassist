package com.claimassist.ingestion;

/**
 * Result of a single {@link IngestionService#ingest} call. Carries counts so adapters can aggregate
 * across batches, and a status so a future async/202 flow fits without reshaping.
 */
public record IngestionResult(
    String sourceId, int chunksCreated, int chunksSkipped, IngestionStatus status) {

  public enum IngestionStatus {
    SUCCESS, // all chunks stored (no duplicates)
    PARTIAL, // mixed — at least one new chunk, at least one duplicate skipped
    SKIPPED, // all chunks were duplicates, or content was empty after normalization
    FAILED // processing error (exception path — not returned normally)
  }
}
