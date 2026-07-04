package com.claimassist.common.dto;

import java.time.Instant;

/** Standard error envelope returned by the global exception handler. */
public record ApiError(String errorCode, String message, String requestId, Instant timestamp) {}
