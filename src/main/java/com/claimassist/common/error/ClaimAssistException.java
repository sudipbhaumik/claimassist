package com.claimassist.common.error;

/** Base unchecked exception for all application-level errors. */
public class ClaimAssistException extends RuntimeException {

  private final ErrorCode errorCode;

  public ClaimAssistException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ClaimAssistException(ErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
