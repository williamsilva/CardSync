package com.cardsync.core.security.web.headers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Predicate;
import org.springframework.security.web.header.HeaderWriter;

public final class ConditionalHstsHeaderWriter implements HeaderWriter {

  private final String value;
  private final Predicate<HttpServletRequest> condition;

  public ConditionalHstsHeaderWriter(
    long maxAgeSeconds,
    boolean includeSubDomains,
    boolean preload,
    Predicate<HttpServletRequest> condition
  ) {
    StringBuilder sb = new StringBuilder();
    sb.append("max-age=").append(maxAgeSeconds);
    if (includeSubDomains) sb.append("; includeSubDomains");
    if (preload) sb.append("; preload");
    this.value = sb.toString();
    this.condition = condition;
  }

  @Override
  public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
    if (condition != null && condition.test(request)) {
      response.setHeader("Strict-Transport-Security", value);
    }
  }
}
