package com.claimassist.api;

import java.util.List;

/** Response from {@code POST /api/v1/ask} — the top-k retrieved chunks with similarity scores. */
public record AskResponse(String question, List<MatchResult> matches, int count) {}
