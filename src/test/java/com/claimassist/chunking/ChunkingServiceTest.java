package com.claimassist.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimassist.config.ClaimAssistProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChunkingServiceTest {

  // Build props inline — no Spring context needed
  private static ClaimAssistProperties props() {
    ClaimAssistProperties props = new ClaimAssistProperties();
    ClaimAssistProperties.Chunk chunk = new ClaimAssistProperties.Chunk();
    chunk.setSize(512);
    chunk.setOverlap(50);
    props.setChunk(chunk);
    return props;
  }

  private final ChunkingService service = new ChunkingService(props());

  @Test
  void shortText_producesAtLeastOneChunk() {
    List<TextChunk> chunks = service.chunk("Short policy text.", Map.of());
    assertThat(chunks).isNotEmpty();
    assertThat(chunks.get(0).text()).contains("Short");
  }

  @Test
  void longText_producesMultipleChunks() {
    // ~1200 words — well over the 512-token chunk size
    String text = ("Insurance policy coverage detail. ").repeat(200);
    List<TextChunk> chunks = service.chunk(text, Map.of("claim_id", "CLM-TEST"));
    assertThat(chunks.size()).isGreaterThan(1);
  }

  @Test
  void metadataAttachedToEveryChunk() {
    String text = ("Word ").repeat(300);
    Map<String, Object> base = Map.of("claim_id", "CLM-1001", "doc_type", "policy");
    List<TextChunk> chunks = service.chunk(text, base);
    for (TextChunk chunk : chunks) {
      assertThat(chunk.metadata()).containsEntry("claim_id", "CLM-1001");
      assertThat(chunk.metadata()).containsEntry("doc_type", "policy");
    }
  }

  @Test
  void chunkIndexAndTotalAttached() {
    String text = ("Coverage clause detail. ").repeat(200);
    List<TextChunk> chunks = service.chunk(text, Map.of());
    assertThat(chunks.get(0).metadata()).containsKey("chunk_index");
    assertThat(chunks.get(0).metadata()).containsKey("chunk_total");
    assertThat(chunks.get(0).metadata().get("chunk_index")).isEqualTo(0);
    assertThat(chunks.get(chunks.size() - 1).metadata().get("chunk_total"))
        .isEqualTo(chunks.size());
  }

  @Test
  void contentHash_isSha256Hex() {
    List<TextChunk> chunks = service.chunk("Hello claim world.", Map.of());
    assertThat(chunks).isNotEmpty();
    // SHA-256 hex string is always 64 chars
    assertThat(chunks.get(0).contentHash()).hasSize(64).matches("[a-f0-9]+");
  }

  @Test
  void sameText_producesSameHash() {
    List<TextChunk> a = service.chunk("Identical claim policy text content.", Map.of());
    List<TextChunk> b = service.chunk("Identical claim policy text content.", Map.of());
    assertThat(a.get(0).contentHash()).isEqualTo(b.get(0).contentHash());
  }

  @Test
  void differentText_producesDifferentHash() {
    List<TextChunk> a = service.chunk("Policy coverage section alpha content.", Map.of());
    List<TextChunk> b = service.chunk("Policy coverage section beta content.", Map.of());
    assertThat(a.get(0).contentHash()).isNotEqualTo(b.get(0).contentHash());
  }
}
