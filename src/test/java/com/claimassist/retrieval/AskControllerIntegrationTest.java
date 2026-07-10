package com.claimassist.retrieval;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext
class AskControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  private static final int DIMS = 768;

  @MockitoBean EmbeddingModel embeddingModel;
  @MockitoBean ChatModel chatModel;

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc.execute("DELETE FROM document_chunks");

    when(embeddingModel.embedForResponse(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> texts = invocation.getArgument(0);
              List<Embedding> embeddings = new ArrayList<>();
              for (int i = 0; i < texts.size(); i++) {
                float[] v = new float[DIMS];
                Arrays.fill(v, 0.1f);
                embeddings.add(new Embedding(v, i));
              }
              return new EmbeddingResponse(embeddings);
            });

    float[] queryVector = new float[DIMS];
    Arrays.fill(queryVector, 0.1f);
    when(embeddingModel.embed(anyString())).thenReturn(queryVector);

    // Stub ChatModel so generation does not need a live Ollama instance
    AssistantMessage msg = new AssistantMessage("The policy covers liability and vehicle damage.");
    ChatResponse chatResponse = new ChatResponse(List.of(new Generation(msg)));
    when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
  }

  @Test
  void ask_withIngestedContent_returnsGroundedAnswer() throws Exception {
    String content =
        ("This policy covers liability and vehicle damage for all registered vehicles. ")
            .repeat(20);
    ingest(
        content, "{\"claim_id\":\"CLM-ASK01\",\"doc_type\":\"policy\",\"source\":\"policy.txt\"}");

    mockMvc
        .perform(
            post("/api/v1/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What does the policy cover?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.question").value("What does the policy cover?"))
        .andExpect(jsonPath("$.answer").isString())
        .andExpect(jsonPath("$.citations").isArray())
        .andExpect(jsonPath("$.usedFallback").value(false));
  }

  @Test
  void ask_emptyStore_usesFallback_modelNotInvoked() throws Exception {
    // Empty store → retriever returns nothing → gate fires → model NOT called, usedFallback=true
    mockMvc
        .perform(
            post("/api/v1/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What is covered?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").isString())
        .andExpect(jsonPath("$.citations").isArray())
        .andExpect(jsonPath("$.citations").isEmpty())
        .andExpect(jsonPath("$.usedFallback").value(true));

    verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
  }

  @Test
  void ask_blankQuestion_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ask_missingQuestion_returns400() throws Exception {
    mockMvc
        .perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ask_withIngestedContent_citationsContainSource() throws Exception {
    String content = ("Insurance policy with detailed coverage terms and exclusions. ").repeat(20);
    ingest(
        content,
        "{\"claim_id\":\"CLM-ASK02\",\"doc_type\":\"policy\",\"source\":\"test.txt\",\"section\":\"Coverage\"}");

    mockMvc
        .perform(
            post("/api/v1/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"coverage terms\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.citations[0].source").value("test.txt"))
        .andExpect(jsonPath("$.citations[0].docType").value("policy"));
  }

  @Test
  void ask_topKOverride_limitsRetrievedChunksAndCitations() throws Exception {
    String content =
        ("Detailed policy section covering various claim types and eligibility criteria. ")
            .repeat(50);
    ingest(content, "{\"claim_id\":\"CLM-ASK03\",\"doc_type\":\"policy\",\"source\":\"big.txt\"}");

    mockMvc
        .perform(
            post("/api/v1/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"policy details\",\"top_k\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.citations.length()").value(lessThanOrEqualTo(2)));
  }

  // -------------------------------------------------------------------------

  private void ingest(String content, String metadataJson) throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/ingest")
                .file(
                    new MockMultipartFile(
                        "file", "test.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8)))
                .part(new MockPart("metadata", metadataJson.getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isOk());
  }
}
