package com.claimassist.generation;

import com.claimassist.common.error.ClaimAssistException;
import com.claimassist.common.error.ErrorCode;
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
 * Orchestrates the generation step: retrieval via {@link ContextAugmentationAdvisor}, model call
 * via {@link ChatClient}, answer extraction, citation building, and metrics.
 *
 * <p>Provider abstraction: {@code ChatClient} only — no Ollama or other provider SDK is referenced.
 */
@Service
public class GenerationService {

  private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

  private final ChatClient chatClient;
  private final DenseDocumentRetriever retriever;
  private final MeterRegistry meterRegistry;

  public GenerationService(
      ChatClient chatClient, DenseDocumentRetriever retriever, MeterRegistry meterRegistry) {
    this.chatClient = chatClient;
    this.retriever = retriever;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Runs retrieval + generation for the given question and returns a grounded answer with citations
   * derived from the retrieved chunks' metadata.
   *
   * @throws ClaimAssistException with {@code SERVICE_UNAVAILABLE} on model failure
   */
  public GenerationResult generate(String question, Integer topKOverride) {
    String requestId = MDC.get("requestId");
    log.debug(
        "generation start requestId={} question=[{}]",
        requestId,
        question.substring(0, Math.min(80, question.length())));

    ContextAugmentationAdvisor advisor =
        new ContextAugmentationAdvisor(retriever, question, topKOverride);
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
      List<Document> chunks =
          (List<Document>)
              ccResponse
                  .context()
                  .getOrDefault(ContextAugmentationAdvisor.CHUNKS_KEY, Collections.emptyList());

      List<Citation> citations = buildCitations(chunks);
      recordTokenUsage(ccResponse.chatResponse());

      log.debug("generation complete requestId={} citations={}", requestId, citations.size());
      return new GenerationResult(answer, citations);

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
