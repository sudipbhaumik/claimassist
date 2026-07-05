package com.claimassist.ingestion;

/**
 * Source-agnostic ingestion request. All adapters (REST upload, notes-sync, future S3/Kafka)
 * translate their input into this record before calling {@link IngestionService#ingest}. No
 * adapter-specific types cross into the core.
 */
public record IngestionRequest(
    String sourceId, // filename | claim_note PK | S3 key — unique per source
    String contentType, // "text/plain" | "application/pdf" | "text/html" | "claim_note"
    byte[] rawContent,
    DocumentMetadata metadata) {}
