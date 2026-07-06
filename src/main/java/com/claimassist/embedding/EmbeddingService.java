package com.claimassist.embedding;

import com.claimassist.chunking.TextChunk;
import com.claimassist.common.error.ClaimAssistException;
import com.claimassist.common.error.ErrorCode;
import com.claimassist.config.ClaimAssistProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

/**
 * Embeds {@link TextChunk} lists through Spring AI's {@link EmbeddingModel} in configurable
 * batches. Validates that every returned vector is exactly 768-dimensional and non-zero. Any
 * failure throws loudly — no partial or silent stores.
 *
 * <p>Metrics emitted per batch: {@code claimassist.embed.latency} (Timer), {@code
 * claimassist.embed.tokens} (Counter from EmbeddingResponseMetadata usage), {@code
 * claimassist.embed.batch.size} (DistributionSummary).
 */
@Service
public class EmbeddingService {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

  private final EmbeddingModel embeddingModel;
  private final int batchSize;
  final int expectedDimensions;
  private final MeterRegistry meterRegistry;

  public EmbeddingService(
      EmbeddingModel embeddingModel, ClaimAssistProperties props, MeterRegistry meterRegistry) {
    this.embeddingModel = embeddingModel;
    this.batchSize = props.getEmbed().getBatchSize();
    this.expectedDimensions = props.getEmbed().getExpectedDimensions();
    this.meterRegistry = meterRegistry;
  }

  /**
   * Embeds all chunks and returns one {@code float[]} per chunk in the same order.
   *
   * @throws ClaimAssistException if any batch fails, any vector has wrong dimensions, or any vector
   *     is all-zeros.
   */
  public List<float[]> embed(List<TextChunk> chunks) {
    if (chunks.isEmpty()) {
      return List.of();
    }

    List<String> texts = chunks.stream().map(TextChunk::text).toList();
    List<float[]> result = new ArrayList<>(chunks.size());
    int batchNumber = 0;

    for (int start = 0; start < texts.size(); start += batchSize) {
      int end = Math.min(start + batchSize, texts.size());
      List<String> batch = texts.subList(start, end);
      batchNumber++;

      Timer.Sample sample = Timer.start(meterRegistry);
      try {
        EmbeddingResponse response = embeddingModel.embedForResponse(batch);

        sample.stop(
            Timer.builder("claimassist.embed.latency")
                .description("Embedding batch latency")
                .register(meterRegistry));

        recordTokenUsage(response);
        meterRegistry.summary("claimassist.embed.batch.size").record(batch.size());

        List<float[]> batchVectors =
            response.getResults().stream().map(Embedding::getOutput).toList();

        validateVectors(batchVectors, start);
        result.addAll(batchVectors);

        log.debug(
            "embed.batch batchNumber={} batchSize={} startIndex={}",
            batchNumber,
            batch.size(),
            start);

      } catch (ClaimAssistException e) {
        throw e;
      } catch (Exception e) {
        throw new ClaimAssistException(
            ErrorCode.INTERNAL_ERROR,
            "Embedding batch " + batchNumber + " failed: " + e.getMessage(),
            e);
      }
    }

    log.info("embed.complete totalChunks={} batches={}", chunks.size(), batchNumber);
    return result;
  }

  private void recordTokenUsage(EmbeddingResponse response) {
    if (response.getMetadata() == null) return;
    var usage = response.getMetadata().getUsage();
    if (usage == null) return;
    Integer tokens = usage.getTotalTokens();
    if (tokens != null && tokens > 0) {
      meterRegistry.counter("claimassist.embed.tokens").increment(tokens.doubleValue());
    }
  }

  private void validateVectors(List<float[]> vectors, int startIndex) {
    for (int i = 0; i < vectors.size(); i++) {
      float[] v = vectors.get(i);
      int chunkIndex = startIndex + i;

      if (v == null) {
        throw new ClaimAssistException(
            ErrorCode.INTERNAL_ERROR, "Null embedding vector at chunk index " + chunkIndex);
      }
      if (v.length != expectedDimensions) {
        throw new ClaimAssistException(
            ErrorCode.INTERNAL_ERROR,
            "Expected "
                + expectedDimensions
                + "-dim vector but got "
                + v.length
                + " at chunk index "
                + chunkIndex);
      }
      if (isAllZero(v)) {
        throw new ClaimAssistException(
            ErrorCode.INTERNAL_ERROR,
            "All-zero embedding vector at chunk index "
                + chunkIndex
                + " — model returned a degenerate embedding");
      }
    }
  }

  private static boolean isAllZero(float[] v) {
    for (float f : v) {
      if (f != 0.0f) return false;
    }
    return true;
  }
}
