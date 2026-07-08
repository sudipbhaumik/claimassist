package com.claimassist.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.claimassist.common.error.ClaimAssistException;
import com.claimassist.common.error.ErrorCode;
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
 * <p>The real ChatClient chain executes advisors inside DefaultChatClient.call() — but in this test
 * the entire ChatClient chain is mocked, so ContextAugmentationAdvisor.before() is never invoked.
 * We pre-populate the response context with the chunk list that the advisor would have stored, so
 * citation extraction can be verified without executing the real advisor flow (which is tested in
 * ContextAugmentationAdvisorTest instead).
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
    service = new GenerationService(chatClient, retriever, meterRegistry);
  }

  @Test
  void generate_returnsAnswerAndCitationsFromContext() {
    Document doc =
        new Document(
            "Liability is covered for all registered vehicles.",
            Map.of("source", "policy.pdf", "section", "Coverage", "doc_type", "policy"));

    // Context is pre-populated as the real advisor would set it
    stubChatClientChain(buildChatClientResponse("Liability is covered.", List.of(doc)));

    GenerationResult result = service.generate("What is covered?", 3);

    assertThat(result.answer()).isEqualTo("Liability is covered.");
    assertThat(result.citations()).hasSize(1);
    assertThat(result.citations().get(0).source()).isEqualTo("policy.pdf");
    assertThat(result.citations().get(0).section()).isEqualTo("Coverage");
    assertThat(result.citations().get(0).docType()).isEqualTo("policy");
  }

  @Test
  void generate_emptyContextChunks_returnsEmptyCitations() {
    stubChatClientChain(buildChatClientResponse("I don't have enough information.", List.of()));

    GenerationResult result = service.generate("Unknown question?", null);

    assertThat(result.citations()).isEmpty();
    assertThat(result.answer()).isNotNull();
  }

  @Test
  void generate_nullUsage_doesNotThrow() {
    // ChatResponse with no metadata/usage (typical for Ollama responses)
    AssistantMessage msg = new AssistantMessage("Answer.");
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(msg)));
    stubChatClientChain(new ChatClientResponse(chatResponse, Map.of()));

    GenerationResult result = service.generate("question?", null);

    assertThat(result.answer()).isEqualTo("Answer.");
  }

  @Test
  void generate_modelException_throwsServiceUnavailable() {
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
    stubChatClientChain(buildChatClientResponse("answer", List.of()));

    service.generate("question?", null);

    assertThat(meterRegistry.find("claimassist.ask.latency").timer()).isNotNull();
  }

  @Test
  void generate_duplicateSources_deduplicatedInCitations() {
    Document doc1 =
        new Document(
            "Text 1.", Map.of("source", "policy.pdf", "section", "S1", "doc_type", "policy"));
    Document doc2 =
        new Document(
            "Text 2.", Map.of("source", "policy.pdf", "section", "S1", "doc_type", "policy"));

    stubChatClientChain(buildChatClientResponse("answer", List.of(doc1, doc2)));

    GenerationResult result = service.generate("question?", 2);

    // Citation records are value-equal when all fields match — distinct() deduplicates
    assertThat(result.citations()).hasSize(1);
  }

  // -------------------------------------------------------------------------

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
}
