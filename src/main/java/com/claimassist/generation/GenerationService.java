package com.claimassist.generation;

import com.claimassist.common.error.ClaimAssistException;
import com.claimassist.common.error.ErrorCode;
import com.claimassist.config.ClaimAssistProperties;
import com.claimassist.retrieval.DenseDocumentRetriever;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the ask pipeline: retrieve → fallback gate → generate.
 *
 * <p>The fallback gate is deterministic: if the retriever returns no chunks, or the top similarity
 * score is below {@code claimassist.retrieval.threshold}, the model is NOT called and a safe
 * configured message is returned immediately. This guarantees the model only runs on grounded
 * context.
 *
 * <p>Provider abstraction: {@code ChatClient} only — no Ollama or other provider SDK referenced.
 */
@Service
public class GenerationService {

  private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

  private final ChatClient chatClient;
  private final DenseDocumentRetriever retriever;
  private final MeterRegistry meterRegistry;
  private final double threshold;
  private final String fallbackMessage;

  public GenerationService(
      ChatClient chatClient,
      DenseDocumentRetriever retriever,
      MeterRegistry meterRegistry,
      ClaimAssistProperties props) {
    this.chatClient = chatClient;
    this.retriever = retriever;
    this.meterRegistry = meterRegistry;
    this.threshold = props.getRetrieval().getThreshold();
    this.fallbackMessage = props.getGuardrail().getFallbackMessage();
  }

  /**
   * Runs retrieval, applies the grounding fallback gate, then invokes the model only when
   * sufficient context exists.
   *
   * @return {@link GenerationResult} with {@code usedFallback=true} when the gate fired (no model
   *     call); {@code usedFallback=false} with a grounded cited answer otherwise
   * @throws ClaimAssistException with {@code SERVICE_UNAVAILABLE} on model failure
   */
  public GenerationResult generate(String question, Integer topKOverride) {
    String requestId = MDC.get("requestId");
    log.debug(
        "generation start requestId={} question=[{}]",
        requestId,
        question.substring(0, Math.min(80, question.length())));

    List<Document> chunks = retriever.retrieve(question, topKOverride);

    if (shouldFallback(chunks)) {
      String qHash = Integer.toHexString(question.hashCode());
      log.info(
          "fallback fired requestId={} qHash={} chunks={} threshold={}",
          requestId,
          qHash,
          chunks.size(),
          threshold);
      meterRegistry.counter("claimassist.ask.fallback").increment();
      return new GenerationResult(fallbackMessage, Collections.emptyList(), true);
    }

    ContextAugmentationAdvisor advisor = new ContextAugmentationAdvisor(chunks);
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      ChatClientResponse ccResponse =
          chatClient.prompt().user(question).advisors(advisor).call().chatClientResponse();

      sample.stop(
          Timer.builder("claimassist.ask.latency")
              .description("Ask end-to-end latency (retrieval + generation)")
              .register(meterRegistry));

      String answer = ccResponse.chatResponse().getResult().getOutput().getText();

      @SuppressWarnings("unchecked")
      List<Document> responseChunks =
          (List<Document>)
              ccResponse
                  .context()
                  .getOrDefault(ContextAugmentationAdvisor.CHUNKS_KEY, Collections.emptyList());

      List<Citation> citations = buildCitations(responseChunks);
      recordTokenUsage(ccResponse.chatResponse());

      log.debug("generation complete requestId={} citations={}", requestId, citations.size());
      return new GenerationResult(answer, citations, false);

    } catch (ClaimAssistException e) {
      sample.stop(Timer.builder("claimassist.ask.latency").register(meterRegistry));
      throw e;
    } catch (Exception e) {
      sample.stop(Timer.builder("claimassist.ask.latency").register(meterRegistry));
      log.warn("generation failed requestId={}: {}", requestId, e.getMessage());
      throw new ClaimAssistException(
          ErrorCode.SERVICE_UNAVAILABLE, "Generation service unavailable", e);
    }
  }

  /**
   * Returns {@code true} when retrieval produced no usable context. With {@code threshold=0.0}
   * (the default), this fires only on empty results — pgvector has already applied the same
   * threshold filter. When {@code threshold > 0}, the score check is belt-and-suspenders against
   * chunks that slipped through with null or borderline scores.
   */
  private boolean shouldFallback(List<Document> chunks) {
    if (chunks.isEmpty()) return true;
    double topScore =
        chunks.stream()
            .filter(c -> c.getScore() != null)
            .mapToDouble(Document::getScore)
            .max()
            .orElse(0.0);
    return topScore < threshold;
  }

  private List<Citation> buildCitations(List<Document> chunks) {
    return chunks.stream()
        .map(
            doc -> {
              Map<String, Object> meta = doc.getMetadata();
              return new Citation(
                  (String) meta.get("source"),
                  (String) meta.get("section"),
                  (String) meta.get("doc_type"));
            })
        .distinct()
        .toList();
  }

  private void recordTokenUsage(ChatResponse chatResponse) {
    if (chatResponse == null || chatResponse.getMetadata() == null) return;
    Usage usage = chatResponse.getMetadata().getUsage();
    if (usage == null) return;
    Integer promptTokens = usage.getPromptTokens();
    Integer completionTokens = usage.getCompletionTokens();
    if (promptTokens != null) {
      meterRegistry.counter("claimassist.ask.tokens", "type", "prompt").increment(promptTokens);
    }
    if (completionTokens != null) {
      meterRegistry
          .counter("claimassist.ask.tokens", "type", "completion")
          .increment(completionTokens);
    }
  }
}
