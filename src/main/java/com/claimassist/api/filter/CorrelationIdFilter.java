package com.claimassist.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propagates a request-scoped correlation ID through MDC so all log statements within a request
 * share a traceable identifier. Reads X-Request-Id from the incoming request or generates one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  static final String REQUEST_ID_MDC_KEY = "requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String requestId =
        Optional.ofNullable(request.getHeader(REQUEST_ID_HEADER))
            .filter(s -> !s.isBlank())
            .orElse(UUID.randomUUID().toString());

    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(REQUEST_ID_MDC_KEY);
    }
  }
}
