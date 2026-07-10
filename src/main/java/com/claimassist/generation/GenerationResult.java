package com.claimassist.generation;

import java.util.List;

/**
 * Internal result from {@link GenerationService} — answer text plus citations from retrieved
 * chunks.
 */
public record GenerationResult(String answer, List<Citation> citations, boolean usedFallback) {}
