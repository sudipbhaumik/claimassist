package com.claimassist.store;

import java.time.LocalDate;
import java.util.UUID;

/** Row projection for {@code claim_notes} table. */
public record ClaimNote(
    UUID id, String claimId, String policyId, String author, String noteText, LocalDate noteDate) {}
