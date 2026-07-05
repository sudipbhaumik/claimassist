package com.claimassist.store;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads un-ingested rows from {@code claim_notes} and marks them ingested after processing. */
@Repository
public class ClaimNoteRepository {

  private final JdbcTemplate jdbc;

  public ClaimNoteRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Returns all rows where {@code ingested = false}, ordered by {@code created_at}. */
  public List<ClaimNote> findUnIngested() {
    return jdbc.query(
        """
        SELECT id, claim_id, policy_id, author, note_text, note_date
        FROM claim_notes
        WHERE ingested = false
        ORDER BY created_at
        """,
        (rs, rowNum) ->
            new ClaimNote(
                UUID.fromString(rs.getString("id")),
                rs.getString("claim_id"),
                rs.getString("policy_id"),
                rs.getString("author"),
                rs.getString("note_text"),
                rs.getObject("note_date", LocalDate.class)));
  }

  /** Flips the {@code ingested} watermark. Call after the note has been processed. */
  public void markIngested(UUID id) {
    jdbc.update("UPDATE claim_notes SET ingested = true WHERE id = ?::uuid", id.toString());
  }
}
