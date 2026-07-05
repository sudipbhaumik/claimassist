package com.claimassist.ingestion.normalize;

import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic, lossless text normalization. Applied to parsed text before chunking and
 * content_hash calculation so that cosmetically different versions of the same content produce
 * identical hashes.
 *
 * <p>Rules (in order): normalize line endings, strip non-printable control chars (preserving tab
 * and newline), collapse horizontal whitespace runs, strip per-line leading/trailing whitespace,
 * remove blank lines, final trim.
 */
@Component
public class DocumentNormalizer {

  // ASCII control chars except TAB (0x09) and LF (0x0A): NUL-BS, VT, FF, SO-US, DEL
  private static final String CONTROL_CHAR_PATTERN = "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]";

  public String normalize(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    return text.replaceAll("\r\n|\r", "\n")
        .replaceAll(CONTROL_CHAR_PATTERN, "")
        .replaceAll("[ \t]+", " ")
        .lines()
        .map(String::strip)
        .filter(l -> !l.isEmpty())
        .collect(Collectors.joining("\n"))
        .strip();
  }
}
