package com.claimassist.chunking;

import com.claimassist.config.ClaimAssistProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

/**
 * Splits normalized text into embeddable chunks. Strategy is selected by {@code doc_type}:
 * policy/correspondence → structure-aware (heading-based with overlap); everything else →
 * token-based (existing behaviour). Falls back to token-based if structure detection finds no
 * headings or throws.
 */
@Service
public class ChunkingService {

  private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

  private final TokenTextSplitter tokenSplitter;
  private final StructureAwareSplitter structureSplitter;
  private final Set<String> structureAwareDocTypes;
  private final Counter structureAwareCounter;
  private final Counter tokenCounter;

  public ChunkingService(ClaimAssistProperties props, MeterRegistry meterRegistry) {
    ClaimAssistProperties.Chunk chunk = props.getChunk();
    this.tokenSplitter =
        TokenTextSplitter.builder()
            .withChunkSize(chunk.getSize())
            .withMinChunkSizeChars(chunk.getMinChunkSizeChars())
            .withMinChunkLengthToEmbed(chunk.getMinChunkLengthToEmbed())
            .withMaxNumChunks(chunk.getMaxNumChunks())
            .withKeepSeparator(true)
            .build();
    this.structureSplitter =
        new StructureAwareSplitter(
            chunk.getSize(),
            chunk.getOverlap(),
            chunk.getMinChunkLengthToEmbed(),
            chunk.getSectionHeadingMaxChars());
    this.structureAwareDocTypes =
        chunk.getStructureAwareDocTypes() != null
            ? Set.copyOf(chunk.getStructureAwareDocTypes())
            : Set.of("policy", "correspondence");
    this.structureAwareCounter =
        Counter.builder("claimassist.ingest.chunk.strategy")
            .tag("strategy", "structure-aware")
            .register(meterRegistry);
    this.tokenCounter =
        Counter.builder("claimassist.ingest.chunk.strategy")
            .tag("strategy", "token")
            .register(meterRegistry);
  }

  /**
   * Chunks {@code normalizedText}, selecting strategy from {@code doc_type} in {@code
   * baseMetadata}. Adds {@code chunk_index}, {@code chunk_total}, {@code chunk_strategy}, and
   * (structure-aware path) {@code section} to every chunk's metadata.
   */
  public List<TextChunk> chunk(String normalizedText, Map<String, Object> baseMetadata) {
    String docType = (String) baseMetadata.get("doc_type");
    boolean tryStructure = docType != null && structureAwareDocTypes.contains(docType);

    if (tryStructure) {
      try {
        List<StructureAwareSplitter.RawChunk> rawChunks = structureSplitter.split(normalizedText);
        if (!rawChunks.isEmpty()) {
          structureAwareCounter.increment();
          return toTextChunks(rawChunks, baseMetadata);
        }
        log.warn(
            "structure-aware splitting found no headings for doc_type={} claim_id={}; "
                + "falling back to token splitting",
            docType,
            baseMetadata.get("claim_id"));
      } catch (Exception e) {
        log.warn(
            "structure-aware splitting failed for doc_type={} claim_id={}; "
                + "falling back to token splitting",
            docType,
            baseMetadata.get("claim_id"),
            e);
      }
    }

    tokenCounter.increment();
    return tokenChunk(normalizedText, baseMetadata);
  }

  private List<TextChunk> toTextChunks(
      List<StructureAwareSplitter.RawChunk> rawChunks, Map<String, Object> baseMetadata) {
    int total = rawChunks.size();
    return IntStream.range(0, total)
        .mapToObj(
            i -> {
              StructureAwareSplitter.RawChunk rc = rawChunks.get(i);
              Map<String, Object> meta = new HashMap<>(baseMetadata);
              meta.put("chunk_index", i);
              meta.put("chunk_total", total);
              meta.put("chunk_strategy", "structure-aware");
              String sectionHeading = rc.sectionHeading();
              if (sectionHeading != null && !sectionHeading.isEmpty()) {
                meta.put("section", sectionHeading);
              } else {
                // Preamble chunk (content before the first heading) — remove any caller-supplied
                // section to avoid mislabeling pre-heading content with an unrelated section tag.
                meta.remove("section");
              }
              String text = rc.text();
              return new TextChunk(text, sha256(text), Collections.unmodifiableMap(meta));
            })
        .toList();
  }

  private List<TextChunk> tokenChunk(String normalizedText, Map<String, Object> baseMetadata) {
    Document doc = new Document(normalizedText, new HashMap<>(baseMetadata));
    List<Document> chunkDocs = tokenSplitter.split(doc);
    return IntStream.range(0, chunkDocs.size())
        .mapToObj(
            i -> {
              String text = chunkDocs.get(i).getText();
              Map<String, Object> meta = new HashMap<>(baseMetadata);
              meta.put("chunk_index", i);
              meta.put("chunk_total", chunkDocs.size());
              meta.put("chunk_strategy", "token");
              return new TextChunk(text, sha256(text), Collections.unmodifiableMap(meta));
            })
        .toList();
  }

  private String sha256(String text) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
