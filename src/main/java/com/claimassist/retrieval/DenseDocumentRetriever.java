package com.claimassist.retrieval;

import com.claimassist.common.error.ClaimAssistException;
import com.claimassist.common.error.ErrorCode;
import com.claimassist.config.ClaimAssistProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.VectorStoreRetriever;
import org.springframework.stereotype.Component;

/**
 * Dense-only document retriever backed by PgVectorStore.
 *
 * <p>Implements {@link VectorStoreRetriever} — the 1.1.8 retriever interface (Spring AI 2.x renames
 * this to {@code DocumentRetriever}). Exposed as a bean so Stage 2's ContextAugmentationAdvisor
 * (a custom {@code BaseAdvisor}) can inject and call it directly.
 *
 * <p>No filter expression, no fusion, no reranking in this increment. Stage 2 adds sparse + RRF
 * behind this same interface without touching the controller or advisor wiring.
 *
 * <p>Note: {@code PgVectorStore.doSimilaritySearch()} embeds the query text internally via its
 * injected {@code EmbeddingModel} — the same bean used during ingestion. No separate embed call
 * is needed before {@code similaritySearch()}.
 */
@Component
public class DenseDocumentRetriever implements VectorStoreRetriever {

  private static final Logger log = LoggerFactory.getLogger(DenseDocumentRetriever.class);

  private final VectorStore vectorStore;
  private final int defaultTopK;
  private final double defaultThreshold;
  private final MeterRegistry meterRegistry;

  public DenseDocumentRetriever(
      VectorStore vectorStore, ClaimAssistProperties props, MeterRegistry meterRegistry) {
    this.vectorStore = vectorStore;
    this.defaultTopK = props.getRetrieval().getTopK();
    this.defaultThreshold = props.getRetrieval().getThreshold();
    this.meterRegistry = meterRegistry;
  }

  /**
   * Core retrieval — implements the {@link VectorStoreRetriever} contract. Wraps
   * {@code VectorStore.similaritySearch()} with latency + match-count metrics.
   */
  @Override
  public List<Document> similaritySearch(SearchRequest request) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      List<Document> results = vectorStore.similaritySearch(request);
      sample.stop(
          Timer.builder("claimassist.ask.retrieval.latency")
              .description("Dense retrieval latency")
              .register(meterRegistry));
      meterRegistry.summary("claimassist.ask.matches.count").record(results.size());
      log.debug(
          "ask retrieval query=[{}] topK={} threshold={} matched={}",
          request.getQuery().substring(0, Math.min(80, request.getQuery().length())),
          request.getTopK(),
          request.getSimilarityThreshold(),
          results.size());
      return results;
    } catch (ClaimAssistException e) {
      sample.stop(
          Timer.builder("claimassist.ask.retrieval.latency").register(meterRegistry));
      throw e;
    } catch (Exception e) {
      sample.stop(
          Timer.builder("claimassist.ask.retrieval.latency").register(meterRegistry));
      throw new ClaimAssistException(
          ErrorCode.INTERNAL_ERROR, "Dense retrieval failed: " + e.getMessage(), e);
    }
  }

  /**
   * Convenience entry point for the controller and the Stage 2 advisor. Builds a
   * {@link SearchRequest} using config defaults, with an optional per-request {@code topK} override.
   */
  public List<Document> retrieve(String question, Integer topKOverride) {
    int topK = (topKOverride != null && topKOverride > 0) ? topKOverride : defaultTopK;
    SearchRequest request =
        SearchRequest.builder()
            .query(question)
            .topK(topK)
            .similarityThreshold(defaultThreshold)
            .build();
    return similaritySearch(request);
  }
}
