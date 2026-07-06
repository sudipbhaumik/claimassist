package com.claimassist.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class IngestControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @MockitoBean EmbeddingModel embeddingModel;

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  private static final String METADATA_CLM1 =
      """
      {"claim_id":"CLM-IT01","policy_id":"POL-IT01","doc_type":"policy","version":"v1"}
      """;

  @BeforeEach
  void setUpEmbeddingMock() {
    when(embeddingModel.embedForResponse(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> texts = invocation.getArgument(0);
              List<Embedding> embeddings = new ArrayList<>();
              for (int i = 0; i < texts.size(); i++) {
                float[] v = new float[768];
                Arrays.fill(v, 0.1f);
                embeddings.add(new Embedding(v, i));
              }
              return new EmbeddingResponse(embeddings);
            });
  }

  @Test
  void ingestTextDocument_createsRowsInDb() throws Exception {
    String content = ("This is a test policy document covering liability. ").repeat(30);

    mockMvc
        .perform(
            multipart("/api/v1/ingest")
                .file(
                    new MockMultipartFile(
                        "file",
                        "policy.txt",
                        "text/plain",
                        content.getBytes(StandardCharsets.UTF_8)))
                .part(new MockPart("metadata", METADATA_CLM1.getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docsReceived").value(1))
        .andExpect(jsonPath("$.chunksCreated").value(org.hamcrest.Matchers.greaterThan(0)))
        .andExpect(jsonPath("$.chunksEmbedded").value(org.hamcrest.Matchers.greaterThan(0)));

    Integer rows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM document_chunks WHERE metadata->>'claim_id' = 'CLM-IT01'",
            Integer.class);
    assertThat(rows).isGreaterThan(0);
  }

  @Test
  void ingestTextDocument_rowsHaveNonNullEmbeddings() throws Exception {
    String content = ("Embedding verification content for policy processing. ").repeat(30);
    String meta =
        """
        {"claim_id":"CLM-IT04","doc_type":"policy"}
        """;

    mockMvc
        .perform(
            multipart("/api/v1/ingest")
                .file(
                    new MockMultipartFile(
                        "file", "emb.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8)))
                .part(new MockPart("metadata", meta.getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isOk());

    Integer rowsWithEmbedding =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM document_chunks "
                + "WHERE metadata->>'claim_id' = 'CLM-IT04' AND embedding IS NOT NULL",
            Integer.class);
    Integer totalRows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM document_chunks WHERE metadata->>'claim_id' = 'CLM-IT04'",
            Integer.class);

    assertThat(totalRows).isGreaterThan(0);
    assertThat(rowsWithEmbedding).isEqualTo(totalRows);
  }

  @Test
  void repostSameContent_deduplicatesChunks() throws Exception {
    String content = ("Unique dedup test content for policy claim. ").repeat(30);
    String meta =
        """
        {"claim_id":"CLM-IT02","doc_type":"policy"}
        """;
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "doc.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));
    MockPart metaPart = new MockPart("metadata", meta.getBytes(StandardCharsets.UTF_8));

    // First ingest
    mockMvc
        .perform(multipart("/api/v1/ingest").file(file).part(metaPart))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chunksCreated").value(org.hamcrest.Matchers.greaterThan(0)));

    Integer rowsAfterFirst =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM document_chunks WHERE metadata->>'claim_id' = 'CLM-IT02'",
            Integer.class);

    // Second ingest — same bytes
    MockMultipartFile file2 =
        new MockMultipartFile(
            "file", "doc.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));
    MockPart metaPart2 = new MockPart("metadata", meta.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(multipart("/api/v1/ingest").file(file2).part(metaPart2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.duplicatesSkipped").value(org.hamcrest.Matchers.greaterThan(0)));

    Integer rowsAfterSecond =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM document_chunks WHERE metadata->>'claim_id' = 'CLM-IT02'",
            Integer.class);

    assertThat(rowsAfterSecond).isEqualTo(rowsAfterFirst);
  }

  @Test
  void emptyFile_returns400() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/ingest")
                .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                .part(new MockPart("metadata", METADATA_CLM1.getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void metadataStoredAsJsonb() throws Exception {
    String content = ("Policy content with metadata check. ").repeat(30);
    String meta =
        """
        {"claim_id":"CLM-IT03","doc_type":"estimate","jurisdiction":"CA","version":"v2"}
        """;

    mockMvc
        .perform(
            multipart("/api/v1/ingest")
                .file(
                    new MockMultipartFile(
                        "file", "est.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8)))
                .part(new MockPart("metadata", meta.getBytes(StandardCharsets.UTF_8))))
        .andExpect(status().isOk());

    Integer rows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM document_chunks WHERE metadata->>'claim_id' = 'CLM-IT03' "
                + "AND metadata->>'jurisdiction' = 'CA'",
            Integer.class);
    assertThat(rows).isGreaterThan(0);
  }
}
