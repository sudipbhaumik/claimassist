package com.claimassist.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext
class NotesSyncIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void resetDatabase() {
    jdbc.update("DELETE FROM document_chunks");
    jdbc.update("UPDATE claim_notes SET ingested = false");
  }

  @Test
  void syncNotes_ingestsAllUnIngestedRows() throws Exception {
    int unIngestedBefore =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM claim_notes WHERE ingested = false", Integer.class);
    // V3 migration seeds 24 rows all with ingested = false
    assertThat(unIngestedBefore).isEqualTo(24);

    mockMvc
        .perform(post("/api/v1/ingest/notes/sync"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docsReceived").value(24));

    int unIngestedAfter =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM claim_notes WHERE ingested = false", Integer.class);
    assertThat(unIngestedAfter).isZero();
  }

  @Test
  void syncNotes_createsChunksForEachNote() throws Exception {
    // Run sync
    mockMvc.perform(post("/api/v1/ingest/notes/sync")).andExpect(status().isOk());

    // Each note becomes at least one chunk with doc_type = claim_note
    Integer chunks =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM document_chunks WHERE metadata->>'doc_type' = 'claim_note'",
            Integer.class);
    assertThat(chunks).isGreaterThan(0);
  }

  @Test
  void syncNotes_secondCallFindsNoPendingRows() throws Exception {
    // First sync
    mockMvc.perform(post("/api/v1/ingest/notes/sync")).andExpect(status().isOk());

    // Second sync — nothing left to process
    mockMvc
        .perform(post("/api/v1/ingest/notes/sync"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docsReceived").value(0))
        .andExpect(jsonPath("$.chunksCreated").value(0));
  }

  @Test
  void syncNotes_claimIdPersistedInMetadata() throws Exception {
    mockMvc.perform(post("/api/v1/ingest/notes/sync")).andExpect(status().isOk());

    Integer clm1001Chunks =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM document_chunks "
                + "WHERE metadata->>'claim_id' = 'CLM-1001' AND metadata->>'doc_type' = 'claim_note'",
            Integer.class);
    assertThat(clm1001Chunks).isGreaterThan(0);
  }
}
