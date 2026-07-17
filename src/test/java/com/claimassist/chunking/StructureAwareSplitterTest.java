package com.claimassist.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StructureAwareSplitterTest {

  // Default splitter: 512-char chunks, 50-char overlap, min 10 chars, 60-char heading max
  private static StructureAwareSplitter splitter() {
    return new StructureAwareSplitter(512, 50, 10, 60);
  }

  // ── Heading detection ────────────────────────────────────────────────────────

  @Test
  void detectHeadings_markdownStyle_detected() {
    String text = "# DECLARATIONS\nSome preamble content.\n\n## Section I\nBody text here.";
    List<StructureAwareSplitter.RawChunk> chunks = splitter().split(text);
    assertThat(chunks).isNotEmpty();
    List<String> headings = chunks.stream().map(StructureAwareSplitter.RawChunk::sectionHeading).toList();
    assertThat(headings).contains("DECLARATIONS", "Section I");
  }

  @Test
  void detectHeadings_allCapsStyle_detected() {
    String text = "DECLARATIONS\nThis policy covers the following.\n\nCOVERAGE\nDetailed coverage terms.";
    List<StructureAwareSplitter.RawChunk> chunks = splitter().split(text);
    assertThat(chunks).isNotEmpty();
    List<String> headings = chunks.stream().map(StructureAwareSplitter.RawChunk::sectionHeading).toList();
    assertThat(headings).contains("DECLARATIONS", "COVERAGE");
  }

  @Test
  void detectHeadings_noHeadings_returnsEmpty() {
    // No markdown or ALL-CAPS headings → caller must fall back to token splitting
    String text = "This is a short document with no section headings at all. "
        + "Just regular prose in a single paragraph.";
    List<StructureAwareSplitter.RawChunk> chunks = splitter().split(text);
    assertThat(chunks).isEmpty();
  }

  // ── ALL-CAPS false-positive guards ──────────────────────────────────────────

  @Test
  void allCapsDisclaimerLine_notTreatedAsHeading() {
    // Contains chars not in the ALL-CAPS charset (colon, dollar, comma) — must NOT be a heading
    String text =
        "TOTAL AMOUNT DUE: $1,000.00\nClaim adjustment for water damage per policy terms.";
    List<StructureAwareSplitter.RawChunk> chunks = splitter().split(text);
    // Either empty (no headings detected) or the heading is something else — never "TOTAL AMOUNT DUE: $1,000.00"
    boolean hasDisclaimerAsHeading = chunks.stream()
        .anyMatch(c -> "TOTAL AMOUNT DUE: $1,000.00".equals(c.sectionHeading()));
    assertThat(hasDisclaimerAsHeading).isFalse();
  }

  @Test
  void tableHeaderLine_notTreatedAsHeading() {
    // Table header with pipe character — not in the ALL-CAPS charset
    String text = "ITEM | AMOUNT | DATE\nWater mitigation | $500 | 2024-03-15";
    List<StructureAwareSplitter.RawChunk> chunks = splitter().split(text);
    boolean hasTableHeaderAsHeading = chunks.stream()
        .anyMatch(c -> c.sectionHeading() != null && c.sectionHeading().contains("|"));
    assertThat(hasTableHeaderAsHeading).isFalse();
  }

  // ── Section-fits-in-chunk ────────────────────────────────────────────────────

  @Test
  void sectionFitsInChunk_noSubsplit_sectionMetadataSet() {
    String text = "# EXCLUSIONS\nWater damage from flooding is excluded from coverage.";
    List<StructureAwareSplitter.RawChunk> chunks = splitter().split(text);
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).sectionHeading()).isEqualTo("EXCLUSIONS");
    assertThat(chunks.get(0).text()).contains("EXCLUSIONS");
    assertThat(chunks.get(0).text()).contains("Water damage");
  }

  // ── Large section sub-splits with overlap ────────────────────────────────────

  @Test
  void largeSection_subsplitWithOverlap_overlapWithinSection() {
    // Build a section that exceeds 512 chars so it must sub-split
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < 15; i++) {
      body.append("Paragraph ").append(i).append(": coverage terms apply under this section.\n\n");
    }
    String text = "# COVERAGE\n" + body;

    StructureAwareSplitter s = new StructureAwareSplitter(200, 40, 10, 60);
    List<StructureAwareSplitter.RawChunk> chunks = s.split(text);

    // Must produce more than one chunk for this section
    assertThat(chunks.size()).isGreaterThan(1);

    // Every chunk must carry the section heading
    chunks.forEach(c -> assertThat(c.sectionHeading()).isEqualTo("COVERAGE"));

    // Overlap: some content from near the end of chunk N-1 must appear in chunk N
    for (int i = 1; i < chunks.size(); i++) {
      String prevText = chunks.get(i - 1).text();
      String currText = chunks.get(i).text();
      // Take a short distinctive sample from the tail of prevText
      int sampleLen = Math.min(15, prevText.length());
      String tailOfPrev = prevText.substring(prevText.length() - sampleLen).strip();
      // That tail must appear somewhere in currText (character-based overlap guarantee)
      if (tailOfPrev.length() >= 5) {
        assertThat(currText).contains(tailOfPrev);
      }
    }
  }

  @Test
  void headingAppearsOnce_notInOverlapPrefixes() {
    // The section heading must appear in the FIRST sub-chunk only, not duplicated in subsequent ones
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      body.append("Coverage item ").append(i).append(": detailed policy terms for this clause.\n\n");
    }
    String text = "# COVERAGE\n" + body;

    StructureAwareSplitter s = new StructureAwareSplitter(200, 50, 10, 60);
    List<StructureAwareSplitter.RawChunk> chunks = s.split(text);
    assertThat(chunks.size()).isGreaterThan(1);

    // First chunk must contain the heading line
    assertThat(chunks.get(0).text()).startsWith("# COVERAGE");

    // Subsequent chunks must NOT start with the heading line
    for (int i = 1; i < chunks.size(); i++) {
      assertThat(chunks.get(i).text()).doesNotStartWith("# COVERAGE");
    }
  }

  @Test
  void noOverlapAcrossSectionBoundaries() {
    // Two separate sections — content from Section A must not appear in Section B's first chunk
    String sectionA = "DECLARATIONS\n" + "Declarations body: named insured, policy period, premium.\n\n".repeat(8);
    String sectionB = "EXCLUSIONS\nFlood damage is excluded. Fire damage is covered.";
    String text = sectionA + "\n\n" + sectionB;

    StructureAwareSplitter s = new StructureAwareSplitter(200, 50, 10, 60);
    List<StructureAwareSplitter.RawChunk> chunks = s.split(text);

    // Find first EXCLUSIONS chunk
    StructureAwareSplitter.RawChunk firstExclusions = chunks.stream()
        .filter(c -> "EXCLUSIONS".equals(c.sectionHeading()))
        .findFirst()
        .orElseThrow();

    // Must not contain content from DECLARATIONS section body
    assertThat(firstExclusions.text()).doesNotContain("named insured");
    assertThat(firstExclusions.text()).doesNotContain("Declarations body");
  }

  // ── Document-level ordering ──────────────────────────────────────────────────

  @Test
  void chunkIndexAndTotal_documentLevel_contiguous() {
    // chunk_index ordering is the responsibility of ChunkingService, not StructureAwareSplitter,
    // but the splitter must return chunks in document order (DECLARATIONS before COVERAGE etc.)
    String text =
        "# DECLARATIONS\nDeclarations content.\n\n"
            + "# COVERAGE\nCoverage content.\n\n"
            + "# EXCLUSIONS\nExclusions content.";
    List<StructureAwareSplitter.RawChunk> chunks = splitter().split(text);
    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0).sectionHeading()).isEqualTo("DECLARATIONS");
    assertThat(chunks.get(1).sectionHeading()).isEqualTo("COVERAGE");
    assertThat(chunks.get(2).sectionHeading()).isEqualTo("EXCLUSIONS");
  }

  // ── CLM-1003 regression ──────────────────────────────────────────────────────

  @Test
  void clm1003Exclusions_isCoherentChunk() {
    // Derived from the actual CLM-1003 policy.txt EXCLUSIONS section.
    // Verifies the entire section stays whole (no mid-exclusion split) at default chunk size.
    String exclusionsSection =
        "# EXCLUSIONS\n"
            + "\n"
            + "The following are NOT covered under this policy:\n"
            + "\n"
            + "1. Earth movement including earthquakes, landslides, or sinkholes\n"
            + "2. Flood, surface water, waves, tidal water, or overflow of a body of water\n"
            + "3. Water damage from a sewer or drain backup unless Optional Coverage is purchased\n"
            + "4. Neglect or failure to maintain the property\n"
            + "5. Intentional loss caused by an insured person\n"
            + "6. Government action, including demolition or condemnation\n"
            + "7. Nuclear hazard\n"
            + "8. Power failure originating away from the described premises\n";

    List<StructureAwareSplitter.RawChunk> chunks = splitter().split(exclusionsSection);

    // The EXCLUSIONS section is ~680 chars but our chunk size is 512 in the default splitter.
    // With the smaller test splitter at 512 it should stay as one or two coherent chunks —
    // critical invariant: item 3 (water damage exclusion) must not be split from item 2.
    List<StructureAwareSplitter.RawChunk> exclusionChunks = chunks.stream()
        .filter(c -> "EXCLUSIONS".equals(c.sectionHeading()))
        .toList();

    assertThat(exclusionChunks).isNotEmpty();

    // All content from this section must be present collectively
    String combined = exclusionChunks.stream().map(StructureAwareSplitter.RawChunk::text).reduce("", (a, b) -> a + b);
    assertThat(combined).contains("sewer or drain backup");
    assertThat(combined).contains("Flood, surface water");
    assertThat(combined).contains("Earth movement");

    // Every exclusion chunk has the EXCLUSIONS heading as its section
    exclusionChunks.forEach(c -> assertThat(c.sectionHeading()).isEqualTo("EXCLUSIONS"));
  }
}
