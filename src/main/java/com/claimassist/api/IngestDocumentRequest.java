package com.claimassist.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata fields supplied as the "metadata" multipart part in {@code POST /api/v1/ingest}. All
 * fields map from snake_case JSON to camelCase Java via {@code @JsonProperty}. All fields are
 * optional at the API boundary; the core accepts nulls.
 */
public record IngestDocumentRequest(
    @JsonProperty("claim_id") String claimId,
    @JsonProperty("policy_id") String policyId,
    @JsonProperty("policyholder_id") String policyholderId,
    @JsonProperty("assigned_adjuster") String assignedAdjuster,
    @JsonProperty("unit") String unit,
    @JsonProperty("lob") String lob,
    @JsonProperty("doc_type") String docType,
    @JsonProperty("loss_date") String lossDate,
    @JsonProperty("policy_effective_date") String policyEffectiveDate,
    @JsonProperty("policy_expiry_date") String policyExpiryDate,
    @JsonProperty("doc_date") String docDate,
    @JsonProperty("section") String section,
    @JsonProperty("source") String source,
    @JsonProperty("version") String version,
    @JsonProperty("jurisdiction") String jurisdiction) {}
