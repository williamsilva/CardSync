package com.cardsync.core.security.web;

import com.cardsync.api.exceptionhandler.ErrorResponse;
import com.cardsync.core.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

public final class Spa403AccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final MessageSource messages;

  public Spa403AccessDeniedHandler(ObjectMapper objectMapper, Clock clock, MessageSource messages) {
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.messages = messages;
  }

  @Override
  public void handle(
    HttpServletRequest req,
    HttpServletResponse res,
    AccessDeniedException ex
  ) throws IOException {
    Locale locale = req.getLocale();
    Object cid = req.getAttribute(CorrelationIdFilter.ATTR);
    String correlationId = cid != null ? cid.toString() : null;

    ErrorResponse body = new ErrorResponse(
      OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC),
      HttpServletResponse.SC_FORBIDDEN,
      "ACCESS_DENIED",
      "ACCESS_DENIED",
      messages.getMessage("error.ACCESS_DENIED", null, "Access denied", locale),
      ex != null ? ex.getMessage() : "Access denied to resource",
      List.of(),
      correlationId,
      req.getRequestURI(),
      req.getMethod()
    );

    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(res.getOutputStream(), body);
  }
}