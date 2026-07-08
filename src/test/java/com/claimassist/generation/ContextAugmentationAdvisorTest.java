package com.claimassist.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claimassist.retrieval.DenseDocumentRetriever;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

@ExtendWith(MockitoExtension.class)
class ContextAugmentationAdvisorTest {

  @Mock DenseDocumentRetriever retriever;

  private ContextAugmentationAdvisor advisor;

  @BeforeEach
  void setUp() {
    advisor = new ContextAugmentationAdvisor(retriever, "What is covered?", 3);
  }

  @Test
  void before_retrievesChunksAndAugmentsSystemMessage() {
    Document doc =
        new Document(
            "Liability and vehicle damage are covered.",
            Map.of("source", "policy.pdf", "section", "Coverage", "doc_type", "policy"));
    when(retriever.retrieve("What is covered?", 3)).thenReturn(List.of(doc));

    ChatClientRequest request = buildMockRequest();
    AdvisorChain chain = mock(AdvisorChain.class);

    ChatClientRequest result = advisor.before(request, chain);

    assertThat(result).isNotNull();
    // Retriever was called with the question and topK baked into the advisor
    verify(retriever).retrieve("What is covered?", 3);
  }

  @Test
  void before_storesRetrievedChunksInContext() {
    Document doc =
        new Document("Coverage text.", Map.of("source", "policy.pdf", "doc_type", "policy"));
    when(retriever.retrieve(any(), any())).thenReturn(List.of(doc));

    // Capture the context key set on the builder
    ChatClientRequest.Builder builder = mock(ChatClientRequest.Builder.class);
    Prompt prompt = mock(Prompt.class);
    when(prompt.augmentSystemMessage(anyString())).thenReturn(prompt);
    ChatClientRequest request = mock(ChatClientRequest.class);
    when(request.prompt()).thenReturn(prompt);
    when(request.mutate()).thenReturn(builder);
    when(builder.prompt(any())).thenReturn(builder);
    when(builder.context(any(String.class), any())).thenReturn(builder);
    when(builder.build()).thenReturn(mock(ChatClientRequest.class));

    advisor.before(request, mock(AdvisorChain.class));

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
    verify(builder).context(keyCaptor.capture(), valueCaptor.capture());

    assertThat(keyCaptor.getValue()).isEqualTo(ContextAugmentationAdvisor.CHUNKS_KEY);
    @SuppressWarnings("unchecked")
    List<Document> stored = (List<Document>) valueCaptor.getValue();
    assertThat(stored).containsExactly(doc);
  }

  @Test
  void before_emptyChunks_includesNoContextMessage() {
    when(retriever.retrieve(any(), any())).thenReturn(List.of());

    ArgumentCaptor<String> augmentCaptor = ArgumentCaptor.forClass(String.class);
    Prompt prompt = mock(Prompt.class);
    when(prompt.augmentSystemMessage(augmentCaptor.capture())).thenReturn(prompt);

    ChatClientRequest.Builder builder = mock(ChatClientRequest.Builder.class);
    ChatClientRequest request = mock(ChatClientRequest.class);
    when(request.prompt()).thenReturn(prompt);
    when(request.mutate()).thenReturn(builder);
    when(builder.prompt(any())).thenReturn(builder);
    when(builder.context(any(String.class), any())).thenReturn(builder);
    when(builder.build()).thenReturn(mock(ChatClientRequest.class));

    advisor.before(request, mock(AdvisorChain.class));

    assertThat(augmentCaptor.getValue()).contains("No relevant documents were found");
  }

  @Test
  void before_withChunks_contextBlockContainsSourceAndText() {
    Document doc =
        new Document(
            "Collision coverage applies to registered vehicles.",
            Map.of("source", "auto-policy.pdf", "section", "Section 3", "doc_type", "policy"));
    when(retriever.retrieve(any(), any())).thenReturn(List.of(doc));

    ArgumentCaptor<String> augmentCaptor = ArgumentCaptor.forClass(String.class);
    Prompt prompt = mock(Prompt.class);
    when(prompt.augmentSystemMessage(augmentCaptor.capture())).thenReturn(prompt);

    ChatClientRequest.Builder builder = mock(ChatClientRequest.Builder.class);
    ChatClientRequest request = mock(ChatClientRequest.class);
    when(request.prompt()).thenReturn(prompt);
    when(request.mutate()).thenReturn(builder);
    when(builder.prompt(any())).thenReturn(builder);
    when(builder.context(any(String.class), any())).thenReturn(builder);
    when(builder.build()).thenReturn(mock(ChatClientRequest.class));

    advisor.before(request, mock(AdvisorChain.class));

    String contextBlock = augmentCaptor.getValue();
    assertThat(contextBlock).contains("auto-policy.pdf");
    assertThat(contextBlock).contains("Section 3");
    assertThat(contextBlock).contains("Collision coverage applies");
    assertThat(contextBlock).contains("[CONTEXT");
    assertThat(contextBlock).contains("[END CONTEXT]");
  }

  @Test
  void after_passesResponseThroughUnchanged() {
    ChatClientResponse response = mock(ChatClientResponse.class);
    ChatClientResponse result = advisor.after(response, mock(AdvisorChain.class));
    assertThat(result).isSameAs(response);
  }

  // -------------------------------------------------------------------------

  private ChatClientRequest buildMockRequest() {
    Prompt prompt = mock(Prompt.class);
    when(prompt.augmentSystemMessage(anyString())).thenReturn(prompt);

    ChatClientRequest.Builder builder = mock(ChatClientRequest.Builder.class);
    ChatClientRequest request = mock(ChatClientRequest.class);
    when(request.prompt()).thenReturn(prompt);
    when(request.mutate()).thenReturn(builder);
    when(builder.prompt(any())).thenReturn(builder);
    when(builder.context(any(String.class), any())).thenReturn(builder);
    when(builder.build()).thenReturn(mock(ChatClientRequest.class));
    return request;
  }
}
