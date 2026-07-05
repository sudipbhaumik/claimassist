package com.claimassist.api;

import com.claimassist.common.error.ClaimAssistException;
import com.claimassist.common.error.ErrorCode;
import com.claimassist.ingestion.DefaultIngestionService;
import com.claimassist.ingestion.DocumentMetadata;
import com.claimassist.ingestion.IngestionRequest;
import com.claimassist.ingestion.IngestionResult;
import com.claimassist.ingestion.IngestionService;
import com.claimassist.store.ClaimNote;
import com.claimassist.store.ClaimNoteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Thin REST adapters for the ingestion port. Two endpoints, one {@link IngestionService} — all
 * business logic stays in {@link DefaultIngestionService}.
 *
 * <p>{@code POST /api/v1/ingest} — multipart upload (file + JSON metadata part).<br>
 * {@code POST /api/v1/ingest/notes/sync} — reads un-ingested rows from {@code claim_notes}, ingests
 * each through the same port, and marks them ingested.
 */
@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {

  private static final Logger log = LoggerFactory.getLogger(IngestController.class);

  private final IngestionService ingestionService;
  private final ClaimNoteRepository claimNoteRepository;
  private final ObjectMapper objectMapper;
  private final int maxFileSizeBytes;

  public IngestController(
      IngestionService ingestionService,
      ClaimNoteRepository claimNoteRepository,
      ObjectMapper objectMapper,
      @Value("${claimassist.ingest.max-file-size-mb:50}") int maxFileSizeMb) {
    this.ingestionService = ingestionService;
    this.claimNoteRepository = claimNoteRepository;
    this.objectMapper = objectMapper;
    this.maxFileSizeBytes = maxFileSizeMb * 1024 * 1024;
  }

  /**
   * Upload a document and store its chunks. Metadata is a JSON string in the {@code metadata} part.
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<IngestResponse> ingest(
      @RequestPart("file") MultipartFile file, @RequestPart("metadata") String metadataJson)
      throws IOException {

    if (file.isEmpty()) {
      throw new ClaimAssistException(ErrorCode.VALIDATION_ERROR, "Uploaded file is empty");
    }
    if (file.getSize() > maxFileSizeBytes) {
      throw new ClaimAssistException(
          ErrorCode.VALIDATION_ERROR,
          "File size " + file.getSize() + " bytes exceeds limit of " + maxFileSizeBytes + " bytes");
    }

    IngestDocumentRequest dto = parseMetadata(metadataJson);
    String filename =
        StringUtils.hasText(file.getOriginalFilename())
            ? file.getOriginalFilename()
            : (dto.source() != null ? dto.source() : "unknown");

    IngestionRequest req =
        new IngestionRequest(
            filename,
            resolveContentType(file.getContentType(), filename),
            file.getBytes(),
            toDocumentMetadata(dto, filename));

    IngestionResult result = ingestionService.ingest(req);

    return ResponseEntity.ok(
        new IngestResponse(
            1,
            result.chunksCreated(),
            result.chunksCreated(),
            result.chunksSkipped(),
            List.of(
                new IngestResponse.DocResult(
                    result.sourceId(),
                    result.chunksCreated(),
                    result.chunksSkipped(),
                    result.status().name()))));
  }

  /**
   * Reads all un-ingested rows from {@code claim_notes}, ingests each through the same port, and
   * marks them ingested. Per-note errors are logged and counted but do not abort the batch — failed
   * notes stay {@code ingested = false} and can be retried.
   */
  @PostMapping("/notes/sync")
  public ResponseEntity<IngestResponse> syncNotes() {
    List<ClaimNote> pending = claimNoteRepository.findUnIngested();
    log.info("notes.sync found {} un-ingested notes", pending.size());

    int totalCreated = 0;
    int totalSkipped = 0;
    int failed = 0;
    List<IngestResponse.DocResult> perDoc = new ArrayList<>();

    for (ClaimNote note : pending) {
      try {
        IngestionRequest req =
            new IngestionRequest(
                note.id().toString(),
                "claim_note",
                note.noteText().getBytes(StandardCharsets.UTF_8),
                new DocumentMetadata(
                    note.claimId(),
                    note.policyId(),
                    null,
                    note.author(),
                    null,
                    null,
                    "claim_note",
                    null,
                    null,
                    null,
                    note.noteDate() != null ? note.noteDate().toString() : null,
                    null,
                    "claim_notes",
                    null,
                    null));

        IngestionResult result = ingestionService.ingest(req);
        claimNoteRepository.markIngested(note.id());

        totalCreated += result.chunksCreated();
        totalSkipped += result.chunksSkipped();
        perDoc.add(
            new IngestResponse.DocResult(
                result.sourceId(),
                result.chunksCreated(),
                result.chunksSkipped(),
                result.status().name()));
      } catch (Exception e) {
        log.error("notes.sync failed for noteId={}: {}", note.id(), e.getMessage());
        failed++;
      }
    }

    log.info(
        "notes.sync complete docsProcessed={} chunksCreated={} failed={}",
        pending.size() - failed,
        totalCreated,
        failed);

    return ResponseEntity.ok(
        new IngestResponse(pending.size(), totalCreated, totalCreated, totalSkipped, perDoc));
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private IngestDocumentRequest parseMetadata(String json) {
    try {
      return objectMapper.readValue(json, IngestDocumentRequest.class);
    } catch (JsonProcessingException e) {
      throw new ClaimAssistException(
          ErrorCode.VALIDATION_ERROR, "Invalid metadata JSON: " + e.getOriginalMessage());
    }
  }

  private String resolveContentType(String uploadedType, String filename) {
    if (StringUtils.hasText(uploadedType)
        && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(uploadedType)) {
      return uploadedType;
    }
    String lower = filename.toLowerCase();
    if (lower.endsWith(".pdf")) return "application/pdf";
    if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
    return "text/plain";
  }

  private DocumentMetadata toDocumentMetadata(IngestDocumentRequest dto, String filename) {
    return new DocumentMetadata(
        dto.claimId(),
        dto.policyId(),
        dto.policyholderId(),
        dto.assignedAdjuster(),
        dto.unit(),
        dto.lob(),
        dto.docType(),
        dto.lossDate(),
        dto.policyEffectiveDate(),
        dto.policyExpiryDate(),
        dto.docDate(),
        dto.section(),
        dto.source() != null ? dto.source() : filename,
        dto.version(),
        dto.jurisdiction());
  }
}
