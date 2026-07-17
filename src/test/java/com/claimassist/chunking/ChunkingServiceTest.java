package com.claimassist.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimassist.config.ClaimAssistProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChunkingServiceTest {

  private static ClaimAssistProperties props() {
    ClaimAssistProperties props = new ClaimAssistProperties();
    ClaimAssistProperties.Chunk chunk = new ClaimAssistProperties.Chunk();
    chunk.setSize(512);
    chunk.setOverlap(50);
    props.setChunk(chunk);
    return props;
  }

  private final ChunkingService service = new ChunkingService(props(), new SimpleMeterRegistry());

  // ── Existing token-path tests ─────────────────────────────────────────────

  @Test
  void shortText_producesAtLeastOneChunk() {
    List<TextChunk> chunks = service.chunk("Short policy text.", Map.of());
    assertThat(chunks).isNotEmpty();
    assertThat(chunks.get(0).text()).contains("Short");
  }

  @Test
  void longText_producesMultipleChunks() {
    String text = ("Insurance policy coverage detail. ").repeat(200);
    List<TextChunk> chunks = service.chunk(text, Map.of("claim_id", "CLM-TEST"));
    assertThat(chunks.size()).isGreaterThan(1);
  }

  @Test
  void metadataAttachedToEveryChunk() {
    String text = ("Word ").repeat(300);
    Map<String, Object> base = Map.of("claim_id", "CLM-1001", "doc_type", "claim_note");
    List<TextChunk> chunks = service.chunk(text, base);
    for (TextChunk chunk : chunks) {
      assertThat(chunk.metadata()).containsEntry("claim_id", "CLM-1001");
      assertThat(chunk.metadata()).containsEntry("doc_type", "claim_note");
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

  // ── Strategy selection ────────────────────────────────────────────────────

  @Test
  void policyDocType_usesStructureAwareStrategy() {
    // Policy text with a detectable heading triggers the structure-aware path
    String text = "# DECLARATIONS\nNamed insured: John Smith.\n\n# COVERAGE\nDwelling: $300,000.";
    Map<String, Object> meta = Map.of("doc_type", "policy", "claim_id", "CLM-1001");
    List<TextChunk> chunks = service.chunk(text, meta);
    assertThat(chunks).isNotEmpty();
    chunks.forEach(c -> assertThat(c.metadata()).containsEntry("chunk_strategy", "structure-aware"));
  }

  @Test
  void correspondenceDocType_usesStructureAwareStrategy() {
    String text = "# Introduction\nDear Mr. Smith,\n\n# Decision\nYour claim has been reviewed.";
    Map<String, Object> meta = Map.of("doc_type", "correspondence", "claim_id", "CLM-1001");
    List<TextChunk> chunks = service.chunk(text, meta);
    assertThat(chunks).isNotEmpty();
    chunks.forEach(c -> assertThat(c.metadata()).containsEntry("chunk_strategy", "structure-aware"));
  }

  @Test
  void claimNoteDocType_usesTokenStrategy() {
    String text = ("Adjustment note: damage assessed at field visit. ").repeat(50);
    Map<String, Object> meta = Map.of("doc_type", "claim_note", "claim_id", "CLM-1001");
    List<TextChunk> chunks = service.chunk(text, meta);
    assertThat(chunks).isNotEmpty();
    chunks.forEach(c -> assertThat(c.metadata()).containsEntry("chunk_strategy", "token"));
  }

  @Test
  void unknownDocType_fallsBackToToken() {
    String text = ("Some untyped document content. ").repeat(50);
    Map<String, Object> meta = Map.of("doc_type", "unknown_type", "claim_id", "CLM-1001");
    List<TextChunk> chunks = service.chunk(text, meta);
    assertThat(chunks).isNotEmpty();
    chunks.forEach(c -> assertThat(c.metadata()).containsEntry("chunk_strategy", "token"));
  }

  @Test
  void missingDocType_fallsBackToToken() {
    String text = ("Document without a doc_type metadata key. ").repeat(50);
    List<TextChunk> chunks = service.chunk(text, Map.of());
    assertThat(chunks).isNotEmpty();
    chunks.forEach(c -> assertThat(c.metadata()).containsEntry("chunk_strategy", "token"));
  }

  @Test
  void structureAware_noHeadingsDetected_fallsBackToToken() {
    // doc_type = policy but text has no detectable headings → falls back to token splitting
    String text = ("This is an ordinary paragraph with no headings. ").repeat(100);
    Map<String, Object> meta = Map.of("doc_type", "policy", "claim_id", "CLM-NOHDR");
    List<TextChunk> chunks = service.chunk(text, meta);
    assertThat(chunks).isNotEmpty();
    chunks.forEach(c -> assertThat(c.metadata()).containsEntry("chunk_strategy", "token"));
  }

  // ── section metadata derived from headings ────────────────────────────────

  @Test
  void structureAware_sectionMetadataSet() {
    String text = "# EXCLUSIONS\nFlood damage is excluded.\n\n# LIMITS\nDwelling limit is $300,000.";
    Map<String, Object> meta = Map.of("doc_type", "policy", "claim_id", "CLM-1001");
    List<TextChunk> chunks = service.chunk(text, meta);
    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).metadata()).containsEntry("section", "EXCLUSIONS");
    assertThat(chunks.get(1).metadata()).containsEntry("section", "LIMITS");
  }

  @Test
  void preamble_doesNotInheritCallerSuppliedSection() {
    // Document starts with preamble content before the first heading.
    // The caller-supplied section ("Deductibles") must NOT bleed into the preamble chunk —
    // that would mislabel pre-heading header text as belonging to a specific section.
    // Note: **bold** format prevents the title line from matching the ALL-CAPS heading pattern
    // (same format the real policy documents use for their title headers).
    String text =
        "**PROPERTY INSURANCE POLICY**\nPolicy Number: POL-5503\n"
            + "# DECLARATIONS\nNamed insured: John Smith.\n"
            + "# EXCLUSIONS\nFlood damage is excluded.";
    Map<String, Object> meta =
        Map.of("doc_type", "policy", "claim_id", "CLM-1003", "section", "Deductibles");
    List<TextChunk> chunks = service.chunk(text, meta);

    // Preamble chunk: no section key (derived heading is empty → remove caller-supplied)
    TextChunk preamble = chunks.get(0);
    assertThat(preamble.text()).contains("PROPERTY INSURANCE POLICY");
    assertThat(preamble.metadata()).doesNotContainKey("section");

    // Headed sections: correct derived section overrides caller-supplied
    TextChunk declarations = chunks.stream()
        .filter(c -> "DECLARATIONS".equals(c.metadata().get("section")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected a DECLARATIONS chunk"));
    assertThat(declarations.text()).contains("Named insured");

    TextChunk exclusions = chunks.stream()
        .filter(c -> "EXCLUSIONS".equals(c.metadata().get("section")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected an EXCLUSIONS chunk"));
    assertThat(exclusions.text()).contains("Flood damage");
  }
}
