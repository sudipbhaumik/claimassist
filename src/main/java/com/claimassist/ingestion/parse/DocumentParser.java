package com.claimassist.ingestion.parse;

import com.claimassist.common.error.ClaimAssistException;
import com.claimassist.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

/**
 * Converts raw bytes into a plain-text string. For "claim_note" content, content is already plain
 * text — Tika is skipped. For all other types (text, HTML, PDF) Tika auto-detects from magic bytes
 * and extracts text. The returned string is un-normalized; normalization is the next pipeline step.
 */
@Component
public class DocumentParser {

  private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);

  public String parse(byte[] rawContent, String contentType) {
    // For "claim_note" content, the raw bytes are already plain text. No Tika parsing is needed.
    if ("claim_note".equals(contentType)) {
      return new String(rawContent, StandardCharsets.UTF_8);
    }
    try {
      // For all other content types, use Tika to parse the document. Tika auto-detects the type from magic bytes.
      TikaDocumentReader reader = new TikaDocumentReader(new ByteArrayResource(rawContent));
      List<Document> docs = reader.read();
      // Join all document segments into a single string, separated by double newlines. Filter out any null or blank segments.
      String text =
          docs.stream()
              .map(Document::getText)
              .filter(t -> t != null && !t.isBlank())
              .collect(Collectors.joining("\n\n"));
      log.debug("Tika parsed {} document segment(s), contentType={}", docs.size(), contentType);
      return text;
    } catch (Exception e) {
      throw new ClaimAssistException(
          ErrorCode.VALIDATION_ERROR, "Unable to parse document: " + e.getMessage(), e);
    }
  }
}
