package com.claimassist.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claimassist.common.error.ClaimAssistException;
import com.claimassist.common.error.ErrorCode;
import com.claimassist.config.ClaimAssistProperties;
import com.claimassist.retrieval.DenseDocumentRetriever;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;

/**
 * Unit tests for GenerationService.
 *
 * <p>Since 1.5, retrieval runs directly in GenerationService.generate() before the ChatClient
 * call. The fallback gate fires on empty or low-score results without invoking the model.
 *
 * <p>For tests that reach the model call: the ChatClient chain is fully mocked, so
 * ContextAugmentationAdvisor.before() is never invoked by the framework. Response context is
 * pre-populated in buildChatClientResponse() to simulate what the real advisor would store.
 */
@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

  @Mock ChatClient chatClient;
  @Mock DenseDocumentRetriever retriever;

  private SimpleMeterRegistry meterRegistry;
  private GenerationService service;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service = new GenerationService(chatClient, retriever, meterRegistry, makeProps(0.0, "No info available."));
  }

  // ── Fallback gate tests (key tests of 1.5) ────────────────────────────────

  @Test
  void generate_emptyChunks_firesFallback_modelNotCalled() {
    when(retriever.retrieve(anyString(), any())).thenReturn(List.of());

    GenerationResult result = service.generate("Who invented the telephone?", null);

    assertThat(result.usedFallback()).isTrue();
    assertThat(result.answer()).isEqualTo("No info available.");
    assertThat(result.citations()).isEmpty();
    verify(chatClient, never()).prompt();
  }

  @Test
  void generate_nullScoresBelowThreshold_firesFallback_modelNotCalled() {
    // threshold=0.5; Document with null score → orElse(0.0) → 0.0 < 0.5 → fallback
    GenerationService svc =
        new GenerationService(chatClient, retriever, meterRegistry, makeProps(0.5, "No info available."));
    Document lowScoreDoc = new Document("Some text.", Map.of("source", "f.txt"));
    when(retriever.retrieve(anyString(), any())).thenReturn(List.of(lowScoreDoc));

    GenerationResult result = svc.generate("question?", null);

    assertThat(result.usedFallback()).isTrue();
    verify(chatClient, never()).prompt();
  }

  @Test
  void generate_sufficientChunks_proceedsToGeneration() {
    Document doc = new Document("Coverage info.", Map.of("source", "p.pdf", "doc_type", "policy"));
    when(retriever.retrieve(anyString(), any())).thenReturn(List.of(doc));
    stubChatClientChain(buildChatClientResponse("Policy covers liability.", List.of(doc)));

    GenerationResult result = service.generate("What is covered?", null);

    assertThat(result.usedFallback()).isFalse();
    verify(chatClient).prompt();
  }

  @Test
  void generate_fallback_recordsFallbackCounter_notTokenMetric() {
    when(retriever.retrieve(anyString(), any())).thenReturn(List.of());

    service.generate("irrelevant question?", null);

    assertThat(meterRegistry.find("claimassist.ask.fallback").counter()).isNotNull();
    assertThat(meterRegistry.find("claimassist.ask.tokens").counter()).isNull();
  }

  // ── Generation path tests ─────────────────────────────────────────────────

  @Test
  void generate_returnsAnswerAndCitationsFromContext() {
    Document doc =
        new Document(
            "Liability is covered for all registered vehicles.",
            Map.of("source", "policy.pdf", "section", "Coverage", "doc_type", "policy"));
    when(retriever.retrieve(anyString(), any())).thenReturn(List.of(doc));
    stubChatClientChain(buildChatClientResponse("Liability is covered.", List.of(doc)));

    GenerationResult result = service.generate("What is covered?", 3);

    assertThat(result.answer()).isEqualTo("Liability is covered.");
    assertThat(result.usedFallback()).isFalse();
    assertThat(result.citations()).hasSize(1);
    assertThat(result.citations().get(0).source()).isEqualTo("policy.pdf");
    assertThat(result.citations().get(0).section()).isEqualTo("Coverage");
    assertThat(result.citations().get(0).docType()).isEqualTo("policy");
  }

  @Test
  void generate_nullUsage_doesNotThrow() {
    Document doc = new Document("Text.", Map.of("source", "p.txt", "doc_type", "policy"));
    when(retriever.retrieve(anyString(), any())).thenReturn(List.of(doc));
    // ChatResponse with no metadata/usage (typical for Ollama)
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Answer."))));
    stubChatClientChain(new ChatClientResponse(chatResponse, Map.of(ContextAugmentationAdvisor.CHUNKS_KEY, List.of(doc))));

    GenerationResult result = service.generate("question?", null);

    assertThat(result.answer()).isEqualTo("Answer.");
    assertThat(result.usedFallback()).isFalse();
  }

  @Test
  void generate_modelException_throwsServiceUnavailable() {
    Document doc = new Document("Text.", Map.of("source", "p.txt"));
    when(retriever.retrieve(anyString(), any())).thenReturn(List.of(doc));

    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.advisors(any(ContextAugmentationAdvisor.class))).thenReturn(requestSpec);
    when(requestSpec.call()).thenThrow(new RuntimeException("Ollama timeout"));

    assertThatThrownBy(() -> service.generate("question?", null))
        .isInstanceOf(ClaimAssistException.class)
        .satisfies(
            ex ->
                assertThat(((ClaimAssistException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
  }

  @Test
  void generate_recordsLatencyMetric() {
    Document doc = new Document("Text.", Map.of("source", "p.txt", "doc_type", "policy"));
    when(retriever.retrieve(anyString(), any())).thenReturn(List.of(doc));
    stubChatClientChain(buildChatClientResponse("answer", List.of(doc)));

    service.generate("question?", null);

    assertThat(meterRegistry.find("claimassist.ask.latency").timer()).isNotNull();
  }

  @Test
  void generate_duplicateSources_deduplicatedInCitations() {
    Document doc1 =
        new Document("Text 1.", Map.of("source", "policy.pdf", "section", "S1", "doc_type", "policy"));
    Document doc2 =
        new Document("Text 2.", Map.of("source", "policy.pdf", "section", "S1", "doc_type", "policy"));
    when(retriever.retrieve(anyString(), any())).thenReturn(List.of(doc1, doc2));
    stubChatClientChain(buildChatClientResponse("answer", List.of(doc1, doc2)));

    GenerationResult result = service.generate("question?", 2);

    assertThat(result.citations()).hasSize(1);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private ChatClientResponse buildChatClientResponse(String answerText, List<Document> chunks) {
    AssistantMessage msg = new AssistantMessage(answerText);
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(msg)));
    return new ChatClientResponse(
        chatResponse, Map.of(ContextAugmentationAdvisor.CHUNKS_KEY, chunks));
  }

  private void stubChatClientChain(ChatClientResponse ccResponse) {
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.advisors(any(ContextAugmentationAdvisor.class))).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.chatClientResponse()).thenReturn(ccResponse);
  }

  private static ClaimAssistProperties makeProps(double threshold, String fallbackMsg) {
    ClaimAssistProperties props = new ClaimAssistProperties();
    props.getRetrieval().setThreshold(threshold);
    props.getGuardrail().setFallbackMessage(fallbackMsg);
    return props;
  }
}
