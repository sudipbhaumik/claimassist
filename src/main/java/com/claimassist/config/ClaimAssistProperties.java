package com.claimassist.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated configuration for ClaimAssist. All tunables live here — no magic numbers in
 * code. Keys defined now even for later stages so the config shape is stable.
 */
@ConfigurationProperties(prefix = "claimassist")
@Validated
public record ClaimAssistProperties(
    @Valid Model model,
    @Valid Chunk chunk,
    @Valid Retrieval retrieval,
    @Valid Guardrail guardrail) {

  public record Model(@NotBlank String chat, @NotBlank String embedding) {}

  public record Chunk(@Positive int size, @PositiveOrZero int overlap) {}

  public record Retrieval(
      @Positive int topK, @DecimalMin("0.0") double threshold, @Positive int rrfK) {}

  public record Guardrail(@DecimalMin("0.0") @DecimalMax("1.0") double groundingThreshold) {}
}
