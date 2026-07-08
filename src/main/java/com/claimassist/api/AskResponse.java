package com.claimassist.api;

import com.claimassist.generation.Citation;
import java.util.List;

/** Response from {@code POST /api/v1/ask} — a grounded LLM answer with source citations. */
public record AskResponse(
    String question, String answer, List<Citation> citations, boolean usedFallback) {}
