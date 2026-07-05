package com.claimassist.chunking;

import com.claimassist.config.ClaimAssistProperties;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

/**
 * Splits normalized text into fixed-size token chunks using Spring AI's {@link TokenTextSplitter}.
 * Chunk size is read from {@code claimassist.chunk.size}. TokenTextSplitter 1.1.8 has no overlap
 * parameter (verified from JAR bytecode) — {@code claimassist.chunk.overlap} is preserved for Stage
 * 2 when a custom splitter or sentence-aware splitting can implement it.
 */
@Service
public class ChunkingService {

  private final TokenTextSplitter splitter;

  public ChunkingService(ClaimAssistProperties props) {
    this.splitter =
        TokenTextSplitter.builder()
            .withChunkSize(props.getChunk().getSize())
            .withMinChunkSizeChars(50)
            .withMinChunkLengthToEmbed(10)
            .withMaxNumChunks(10_000)
            .withKeepSeparator(true)
            .build();
  }

  /**
   * Splits {@code normalizedText} and attaches {@code baseMetadata} to every chunk. Adds {@code
   * chunk_index} and {@code chunk_total} per chunk so retrieval can reassemble context.
   */
  public List<TextChunk> chunk(String normalizedText, Map<String, Object> baseMetadata) {
    Document doc = new Document(normalizedText, new HashMap<>(baseMetadata));
    List<Document> chunkDocs = splitter.split(doc);

    return IntStream.range(0, chunkDocs.size())
        .mapToObj(
            i -> {
              String text = chunkDocs.get(i).getText();
              Map<String, Object> meta = new HashMap<>(baseMetadata);
              meta.put("chunk_index", i);
              meta.put("chunk_total", chunkDocs.size());
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
