package com.claimassist.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

@ExtendWith(MockitoExtension.class)
class ContextAugmentationAdvisorTest {

  private static final Document DOC =
      new Document(
          "Liability and vehicle damage are covered.",
          Map.of("source", "policy.pdf", "section", "Coverage", "doc_type", "policy"));

  private ContextAugmentationAdvisor advisor;

  @BeforeEach
  void setUp() {
    advisor = new ContextAugmentationAdvisor(List.of(DOC));
  }

  @Test
  void before_augmentsSystemMessageWithContextBlock() {
    Prompt prompt = mock(Prompt.class);
    when(prompt.augmentSystemMessage(anyString())).thenReturn(prompt);
    ChatClientRequest request = buildMockRequest(prompt);

    advisor.before(request, mock(AdvisorChain.class));

    verify(prompt).augmentSystemMessage(anyString());
  }

  @Test
  void before_storesChunksInContextUnderCorrectKey() {
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
    assertThat(stored).containsExactly(DOC);
  }

  @Test
  void before_contextBlockContainsSourceSectionAndText() {
    ArgumentCaptor<String> augmentCaptor = ArgumentCaptor.forClass(String.class);
    Prompt prompt = mock(Prompt.class);
    when(prompt.augmentSystemMessage(augmentCaptor.capture())).thenReturn(prompt);
    ChatClientRequest request = buildMockRequest(prompt);

    advisor.before(request, mock(AdvisorChain.class));

    String ctx = augmentCaptor.getValue();
    assertThat(ctx).contains("policy.pdf");
    assertThat(ctx).contains("Coverage");
    assertThat(ctx).contains("Liability and vehicle damage are covered.");
    assertThat(ctx).contains("[CONTEXT");
    assertThat(ctx).contains("[END CONTEXT]");
  }

  @Test
  void before_sourceOnlyChunk_usesSourceLabelNotLocation() {
    Document noSection =
        new Document("Text without a section.", Map.of("source", "estimate.txt", "doc_type", "estimate"));
    ContextAugmentationAdvisor a = new ContextAugmentationAdvisor(List.of(noSection));

    ArgumentCaptor<String> augmentCaptor = ArgumentCaptor.forClass(String.class);
    Prompt prompt = mock(Prompt.class);
    when(prompt.augmentSystemMessage(augmentCaptor.capture())).thenReturn(prompt);
    ChatClientRequest request = buildMockRequest(prompt);

    a.before(request, mock(AdvisorChain.class));

    String ctx = augmentCaptor.getValue();
    assertThat(ctx).contains("Source: estimate.txt");
    assertThat(ctx).doesNotContain("Location:");
  }

  @Test
  void after_passesResponseThroughUnchanged() {
    ChatClientResponse response = mock(ChatClientResponse.class);
    ChatClientResponse result = advisor.after(response, mock(AdvisorChain.class));
    assertThat(result).isSameAs(response);
  }

  // -------------------------------------------------------------------------

  private ChatClientRequest buildMockRequest(Prompt prompt) {
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
