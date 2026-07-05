package com.claimassist.ingestion;

/**
 * Tier 1 (identity/scoping) + basic Tier 2 (temporal/document context) metadata supplied by the
 * caller at ingest time. Tier 3 (volatile, live Connector data) is not embedded and is added in
 * Stage 3 by the Connector layer.
 *
 * <p>All fields are nullable — callers provide what they know; ingestion stores nulls as absent
 * JSON keys.
 */
public record DocumentMetadata(
    // Tier 1 — identity / scoping (used for retrieval filters)
    String claimId,
    String policyId,
    String policyholderId,
    String assignedAdjuster,
    String unit,
    String lob,
    String docType,
    // Tier 2 — temporal / document context
    String lossDate,
    String policyEffectiveDate,
    String policyExpiryDate,
    String docDate,
    String section,
    String source,
    String version,
    String jurisdiction) {}
