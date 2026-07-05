package com.claimassist.ingestion;

import com.claimassist.chunking.ChunkingService;
import com.claimassist.chunking.TextChunk;
import com.claimassist.ingestion.IngestionResult.IngestionStatus;
import com.claimassist.ingestion.normalize.DocumentNormalizer;
import com.claimassist.ingestion.parse.DocumentParser;
import com.claimassist.store.DocumentChunkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core ingestion pipeline: parse -> normalize -> build metadata -> chunk -> dedup-store.
 *
 * <p>Runs synchronously in 1.1. The {@link IngestionResult} result object is the designed seam for
 * a future async/202 flow — this method never returns void. No embeddings are produced here; that
 * is increment 1.2.
 */
@Service
public class DefaultIngestionService implements IngestionService {

  private static final Logger log = LoggerFactory.getLogger(DefaultIngestionService.class);

  private final DocumentParser parser;
  private final DocumentNormalizer normalizer;
  private final ChunkingService chunker;
  private final DocumentChunkRepository chunkRepo;
  private final MeterRegistry meterRegistry;

  public DefaultIngestionService(
      DocumentParser parser,
      DocumentNormalizer normalizer,
      ChunkingService chunker,
      DocumentChunkRepository chunkRepo,
      MeterRegistry meterRegistry) {
    this.parser = parser;
    this.normalizer = normalizer;
    this.chunker = chunker;
    this.chunkRepo = chunkRepo;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public IngestionResult ingest(IngestionRequest request) {
    Timer.Sample sample = Timer.start(meterRegistry);
    String contentType = request.contentType();
    try {
      log.info("ingestion.start sourceId={} contentType={}", request.sourceId(), contentType);

      // Parse and normalize the document. Parsing is content-type aware; normalization is not.
      String parsed = parser.parse(request.rawContent(), contentType);
      // Normalization is content-type agnostic; it is a pure text transformation.
      String normalized = normalizer.normalize(parsed);

      if (normalized.isBlank()) {
        log.warn("ingestion.empty sourceId={} (skipped)", request.sourceId());
        return new IngestionResult(request.sourceId(), 0, 0, IngestionStatus.SKIPPED);
      }

      // Compute a SHA-256 hash of the normalized text. This is used for deduplication and metadata.
      String docHash = sha256(normalized);

      Map<String, Object> baseMetadata =
          buildMetadata(request.metadata(), request.sourceId(), docHash);

      // Chunk the normalized text and store each chunk. The chunker is content-type aware.
      List<TextChunk> chunks = chunker.chunk(normalized, baseMetadata);

      int created = 0;
      int skipped = 0;
      for (TextChunk chunk : chunks) {
        if (chunkRepo.upsert(chunk) > 0) {
          created++;
        } else {
          skipped++;
        }
      }

      meterRegistry.counter("claimassist.ingest.chunks", "outcome", "created").increment(created);
      meterRegistry.counter("claimassist.ingest.chunks", "outcome", "skipped").increment(skipped);

      IngestionStatus status = resolveStatus(created, skipped, chunks.size());
      log.info(
          "ingestion.complete sourceId={} chunksCreated={} chunksSkipped={} status={}",
          request.sourceId(),
          created,
          skipped,
          status);
      return new IngestionResult(request.sourceId(), created, skipped, status);

    } finally {
      sample.stop(
          Timer.builder("claimassist.ingest.latency")
              .tag("content_type", contentType)
              .register(meterRegistry));
    }
  }

  private Map<String, Object> buildMetadata(
      DocumentMetadata meta, String sourceId, String docHash) {
    Map<String, Object> map = new LinkedHashMap<>();
    // Tier 1
    putIfNotNull(map, "claim_id", meta.claimId());
    putIfNotNull(map, "policy_id", meta.policyId());
    putIfNotNull(map, "policyholder_id", meta.policyholderId());
    putIfNotNull(map, "assigned_adjuster", meta.assignedAdjuster());
    putIfNotNull(map, "unit", meta.unit());
    putIfNotNull(map, "lob", meta.lob());
    putIfNotNull(map, "doc_type", meta.docType());
    // Tier 2
    putIfNotNull(map, "loss_date", meta.lossDate());
    putIfNotNull(map, "policy_effective_date", meta.policyEffectiveDate());
    putIfNotNull(map, "policy_expiry_date", meta.policyExpiryDate());
    putIfNotNull(map, "doc_date", meta.docDate());
    putIfNotNull(map, "section", meta.section());
    putIfNotNull(map, "source", meta.source() != null ? meta.source() : sourceId);
    putIfNotNull(map, "version", meta.version());
    putIfNotNull(map, "jurisdiction", meta.jurisdiction());
    map.put("content_hash", docHash);
    return map;
  }

  private void putIfNotNull(Map<String, Object> map, String key, String value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private IngestionStatus resolveStatus(int created, int skipped, int total) {
    if (total == 0 || created == 0) return IngestionStatus.SKIPPED;
    if (skipped == 0) return IngestionStatus.SUCCESS;
    return IngestionStatus.PARTIAL;
  }

  private String sha256(String text) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
