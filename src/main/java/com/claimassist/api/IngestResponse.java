package com.claimassist.api;

import java.util.List;

/** Response body returned by both ingest endpoints. */
public record IngestResponse(
    int docsReceived,
    int chunksCreated,
    int chunksStored,
    int duplicatesSkipped,
    List<DocResult> perDoc) {

  /** Per-document breakdown within a batch response. */
  public record DocResult(String sourceId, int chunksCreated, int chunksSkipped, String status) {}
}
