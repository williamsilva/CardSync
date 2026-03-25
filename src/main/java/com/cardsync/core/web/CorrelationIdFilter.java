package com.cardsync.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String ATTR = "correlationId";
  public static final String HEADER = "X-Correlation-Id";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
    throws ServletException, IOException {

    String incoming = request.getHeader(HEADER);
    UUID cid;
    try {
      cid = (incoming != null && !incoming.isBlank()) ? UUID.fromString(incoming) : UUID.randomUUID();
    } catch (Exception e) {
      cid = UUID.randomUUID();
    }

    request.setAttribute(ATTR, cid);
    response.setHeader(HEADER, cid.toString());

    chain.doFilter(request, response);
  }
}
