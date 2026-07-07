package com.claimassist.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code POST /api/v1/ask}. {@code top_k} and {@code claim_id} are optional.
 * {@code claim_id} is accepted but not used for filtering until Stage 2.
 */
public record AskRequest(
    @NotBlank String question,
    @JsonProperty("top_k") @Positive Integer topK,
    @JsonProperty("claim_id") String claimId) {}
