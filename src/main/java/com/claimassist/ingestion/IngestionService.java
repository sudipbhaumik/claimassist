package com.claimassist.ingestion;

/**
 * Ingestion port — the stable seam between all document sources and the parse→normalize→chunk→
 * store pipeline. Adapters (REST, notes-sync, future S3/Kafka/CDC) call this method; the pipeline
 * implementation stays behind this interface.
 *
 * <p>Runs synchronously in 1.1. The result object is designed so a future async path can return a
 * job ID without changing callers — the core never returns void.
 */
public interface IngestionService {
  IngestionResult ingest(IngestionRequest request);
}
