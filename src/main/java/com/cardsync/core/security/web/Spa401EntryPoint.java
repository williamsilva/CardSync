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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

public final class Spa401EntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final MessageSource messages;

  public Spa401EntryPoint(ObjectMapper objectMapper, Clock clock, MessageSource messages) {
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.messages = messages;
  }

  @Override
  public void commence(
    HttpServletRequest req,
    HttpServletResponse res,
    AuthenticationException ex
  ) throws IOException {
    Locale locale = req.getLocale();
    Object cid = req.getAttribute(CorrelationIdFilter.ATTR);
    String correlationId = cid != null ? cid.toString() : null;

    ErrorResponse body = new ErrorResponse(
      OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC),
      HttpServletResponse.SC_UNAUTHORIZED,
      "UNAUTHORIZED",
      "SESSION_EXPIRED",
      messages.getMessage("error.SESSION_EXPIRED", null, "Session expired", locale),
      ex != null ? ex.getMessage() : "Authentication required or session expired",
      List.of(),
      correlationId,
      req.getRequestURI(),
      req.getMethod()
    );

    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(res.getOutputStream(), body);
  }
}