package com.claimassist.common.error;

import com.claimassist.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates all exceptions into a consistent ApiError JSON response. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ClaimAssistException.class)
  public ResponseEntity<ApiError> handleClaimAssist(
      ClaimAssistException ex, HttpServletRequest request) {
    log.warn("Application error [{}]: {}", ex.getErrorCode(), ex.getMessage());
    return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
        .body(new ApiError(ex.getErrorCode().name(), ex.getMessage(), requestId(), Instant.now()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return ResponseEntity.badRequest()
        .body(new ApiError(ErrorCode.VALIDATION_ERROR.name(), message, requestId(), Instant.now()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {}", request.getRequestURI(), ex);
    return ResponseEntity.internalServerError()
        .body(
            new ApiError(
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred",
                requestId(),
                Instant.now()));
  }

  private static String requestId() {
    String id = MDC.get("requestId");
    return id != null ? id : "unknown";
  }
}
