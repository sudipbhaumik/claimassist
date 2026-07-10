package com.claimassist.api;

import com.claimassist.generation.GenerationResult;
import com.claimassist.generation.GenerationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST adapter for {@code POST /api/v1/ask}. Delegates entirely to {@link GenerationService}.
 */
@RestController
@RequestMapping("/api/v1/ask")
public class AskController {

  private static final Logger log = LoggerFactory.getLogger(AskController.class);

  private final GenerationService generationService;

  public AskController(GenerationService generationService) {
    this.generationService = generationService;
  }

  @PostMapping
  public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest req) {
    log.info(
        "ask question=[{}] topK={}",
        req.question().substring(0, Math.min(80, req.question().length())),
        req.topK());

    GenerationResult result = generationService.generate(req.question(), req.topK());
    return ResponseEntity.ok(
        new AskResponse(req.question(), result.answer(), result.citations(), result.usedFallback()));
  }
}
