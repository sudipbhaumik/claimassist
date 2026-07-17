package com.claimassist.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits normalized document text on section headings first, then sub-splits large sections with
 * character-based overlap. Overlap is applied within a section only — never across section
 * boundaries, which would re-introduce topic mixing.
 *
 * <p>Two heading patterns are recognized:
 * <ul>
 *   <li>Markdown: {@code ^(#{1,3})\s+(.+)$}
 *   <li>ALL-CAPS: {@code ^[A-Z][A-Z\s/\-&.]{1,N}[A-Z.]$} where N = sectionHeadingMaxChars - 2
 * </ul>
 *
 * <p>If no headings are detected, {@link #split} returns an empty list — the caller must fall
 * back to token-based splitting.
 */
public class StructureAwareSplitter {

  private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,3})\\s+(.+)$");

  private final Pattern allCapsHeading;
  private final int chunkSize;
  private final int overlap;
  private final int minChunkLengthToEmbed;

  /** A heading + its body text, as extracted from the document. */
  private record Section(String heading, String headingLine, String body) {}

  /**
   * A finalized chunk carrying the clean text and the section heading it belongs to.
   * {@code sectionHeading} is empty when the chunk comes from pre-heading preamble content.
   */
  public record RawChunk(String text, String sectionHeading) {}

  public StructureAwareSplitter(
      int chunkSize, int overlap, int minChunkLengthToEmbed, int sectionHeadingMaxChars) {
    this.chunkSize = chunkSize;
    this.overlap = overlap;
    this.minChunkLengthToEmbed = minChunkLengthToEmbed;
    // Middle portion: between first and last char, so max = sectionHeadingMaxChars - 2
    int maxMiddle = Math.max(1, sectionHeadingMaxChars - 2);
    this.allCapsHeading = Pattern.compile("^[A-Z][A-Z\\s/\\-&.]{1," + maxMiddle + "}[A-Z.]$");
  }

  /**
   * Splits {@code text} into {@link RawChunk}s using section structure.
   *
   * @return non-empty list when headings are detected; empty list when no headings found
   *     (caller must fall back to token splitting)
   */
  public List<RawChunk> split(String text) {
    List<Section> sections = detectSections(text);
    // Return empty if no real headings were found — caller falls back to token splitting
    boolean hasRealHeadings = sections.stream().anyMatch(s -> !s.heading().isEmpty());
    if (!hasRealHeadings) {
      return List.of();
    }
    List<RawChunk> result = new ArrayList<>();
    for (Section section : sections) {
      result.addAll(chunkSection(section));
    }
    return result;
  }

  // ── Section detection ────────────────────────────────────────────────────────

  private List<Section> detectSections(String text) {
    String[] lines = text.split("\\n", -1);
    List<Section> sections = new ArrayList<>();

    String currentHeading = null;
    String currentHeadingLine = null;
    StringBuilder currentBody = new StringBuilder();

    for (String line : lines) {
      String trimmed = line.strip();
      String detectedHeading = detectHeading(trimmed);

      if (detectedHeading != null) {
        flush(sections, currentHeading, currentHeadingLine, currentBody);
        currentHeading = detectedHeading;
        currentHeadingLine = trimmed;
        currentBody = new StringBuilder();
      } else {
        // Accumulate body lines (preserve blank lines for paragraph awareness)
        if (currentBody.length() > 0 || !trimmed.isEmpty()) {
          currentBody.append(line).append("\n");
        }
      }
    }

    // Flush last section
    flush(sections, currentHeading, currentHeadingLine, currentBody);
    return sections;
  }

  private void flush(
      List<Section> sections,
      String heading,
      String headingLine,
      StringBuilder body) {
    if (heading == null) {
      // Pre-heading preamble: only emit if there is meaningful content
      String preamble = body.toString().strip();
      if (preamble.length() >= minChunkLengthToEmbed) {
        sections.add(new Section("", "", preamble));
      }
      return;
    }
    sections.add(new Section(heading, headingLine, body.toString().strip()));
  }

  private String detectHeading(String trimmedLine) {
    if (trimmedLine.isEmpty()) return null;

    Matcher md = MARKDOWN_HEADING.matcher(trimmedLine);
    if (md.matches()) {
      return md.group(2).strip();
    }

    if (allCapsHeading.matcher(trimmedLine).matches()) {
      return trimmedLine;
    }

    return null;
  }

  // ── Section → chunks ─────────────────────────────────────────────────────────

  private List<RawChunk> chunkSection(Section section) {
    String headingLine = section.headingLine();
    String body = section.body();

    String fullText = headingLine.isEmpty() ? body : headingLine + "\n" + body;

    if (fullText.length() <= chunkSize) {
      if (fullText.strip().length() < minChunkLengthToEmbed) return List.of();
      return List.of(new RawChunk(fullText.strip(), section.heading()));
    }
    return subsplit(section);
  }

  /**
   * Sub-splits a section that exceeds {@code chunkSize}. The heading appears only in the first
   * sub-chunk; subsequent sub-chunks start with a character-based overlap prefix taken from the
   * end of the previous body segment (never from the heading itself).
   */
  private List<RawChunk> subsplit(Section section) {
    String heading = section.heading();
    String headingLine = section.headingLine();
    String body = section.body();

    // Split body on any newline sequence. DocumentNormalizer removes blank lines so there are no
    // \n\n boundaries left after normalization — splitting on \n+ treats each line as an atomic
    // unit and works correctly with both normalized (single \n) and raw (double \n) input.
    String[] paras = body.split("\\n+");

    List<RawChunk> result = new ArrayList<>();
    StringBuilder currentBody = new StringBuilder();
    boolean firstChunk = true;

    for (String para : paras) {
      if (para.isEmpty()) continue;
      String separator = currentBody.isEmpty() ? "" : "\n";
      String candidate = currentBody + separator + para;

      // First sub-chunk must leave room for the heading prefix
      int bodyLimit =
          firstChunk ? chunkSize - headingLine.length() - 1 : chunkSize;
      if (bodyLimit < 0) bodyLimit = chunkSize; // heading > chunkSize edge case

      if (!currentBody.isEmpty() && candidate.length() > bodyLimit) {
        // Flush current body segment
        result.add(assembleChunk(headingLine, currentBody.toString(), heading, firstChunk));
        firstChunk = false;

        // Overlap prefix: last `overlap` chars of previous body (not of the heading)
        String prev = currentBody.toString();
        String overlapPrefix =
            prev.length() > overlap ? prev.substring(prev.length() - overlap) : prev;
        currentBody = new StringBuilder(overlapPrefix).append("\n").append(para);
      } else if (currentBody.isEmpty() && para.length() > bodyLimit) {
        // Single line larger than the available space: hard character split (safety net)
        int pos = 0;
        int limit = bodyLimit;
        while (pos < para.length()) {
          int end = Math.min(pos + limit, para.length());
          String slice = para.substring(pos, end);
          result.add(assembleChunk(headingLine, slice, heading, firstChunk));
          firstChunk = false;
          limit = chunkSize; // subsequent slices use full budget
          if (end == para.length()) break; // exhausted — do not create sliding-window tail chunks
          pos = Math.max(pos + 1, end - overlap); // advance with overlap
        }
        currentBody = new StringBuilder();
      } else {
        currentBody = new StringBuilder(candidate);
      }
    }

    if (!currentBody.isEmpty()) {
      result.add(assembleChunk(headingLine, currentBody.toString(), heading, firstChunk));
    }

    return result.stream()
        .filter(rc -> rc.text().strip().length() >= minChunkLengthToEmbed)
        .toList();
  }

  private RawChunk assembleChunk(
      String headingLine, String body, String heading, boolean firstChunk) {
    String text =
        (firstChunk && !headingLine.isEmpty())
            ? (headingLine + "\n" + body).strip()
            : body.strip();
    return new RawChunk(text, heading);
  }
}
