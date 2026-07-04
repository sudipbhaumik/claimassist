package com.claimassist.common.error;

import org.springframework.http.HttpStatus;

/** Canonical error codes used throughout the application and surfaced in ApiError responses. */
public enum ErrorCode {
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

  private final HttpStatus httpStatus;

  ErrorCode(HttpStatus httpStatus) {
    this.httpStatus = httpStatus;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
