package com.claimassist.chunking;

import java.util.Map;

/**
 * Immutable value produced by {@link ChunkingService}. Carries the normalized chunk text, its
 * SHA-256 content hash (used for dedup), and the merged metadata map that will be written to the
 * {@code metadata} jsonb column in {@code document_chunks}.
 */
public record TextChunk(String text, String contentHash, Map<String, Object> metadata) {}
