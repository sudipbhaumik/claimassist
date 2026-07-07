package com.claimassist.api;

/** A single chunk returned by the retrieval step, with its similarity score and metadata. */
public record MatchResult(String content, double score, String source, String section, String docType) {}
