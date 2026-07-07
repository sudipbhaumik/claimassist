package com.claimassist.api;

import com.claimassist.retrieval.DenseDocumentRetriever;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST adapter for the dense retrieval port. No business logic — delegates entirely to
 * {@link DenseDocumentRetriever}. Returns the top-k retrieved chunks with similarity scores;
 * no LLM call in this increment.
 */
@RestController
@RequestMapping("/api/v1/ask")
public class AskController {

  private static final Logger log = LoggerFactory.getLogger(AskController.class);

  private final DenseDocumentRetriever retriever;

  public AskController(DenseDocumentRetriever retriever) {
    this.retriever = retriever;
  }

  @PostMapping
  public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest req) {
    log.info(
        "ask question=[{}] topK={}",
        req.question().substring(0, Math.min(80, req.question().length())),
        req.topK());

    List<Document> docs = retriever.retrieve(req.question(), req.topK());
    List<MatchResult> matches = docs.stream().map(AskController::toMatchResult).toList();

    return ResponseEntity.ok(new AskResponse(req.question(), matches, matches.size()));
  }

  private static MatchResult toMatchResult(Document doc) {
    Map<String, Object> meta = doc.getMetadata();
    return new MatchResult(
        doc.getText(),
        doc.getScore() != null ? doc.getScore() : 0.0,
        (String) meta.get("source"),
        (String) meta.get("section"),
        (String) meta.get("doc_type"));
  }
}
