package com.claimassist.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight application-level ping. Actuator /actuator/health is the primary health signal; this
 * endpoint provides a simple API-versioned liveness check.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

  @GetMapping("/ping")
  public ResponseEntity<Map<String, String>> ping() {
    return ResponseEntity.ok(Map.of("status", "ok"));
  }
}
