package com.cardsync.web.exceptionhandler;

import com.cardsync.core.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
@RequiredArgsConstructor
public class WebExceptionHandler {

  private final Clock clock;

  @ExceptionHandler(NoResourceFoundException.class)
  public String handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest req, Model model) {
    // Se for JSON, deixa o ApiExceptionHandler cuidar
    if (wantsJson(req)) {
      // devolve 404 puro e o ApiExceptionHandler cuidará do resto
      req.setAttribute("javax.servlet.error.status_code", 404);
      return "forward:/error";
    }

    enrich(model, req);
    model.addAttribute("status", 404);
    return "error/404";
  }

  @ExceptionHandler(AccessDeniedException.class)
  public String handleAccessDenied(AccessDeniedException ex, HttpServletRequest req, Model model) {
    if (wantsJson(req)) {
      req.setAttribute("javax.servlet.error.status_code", 403);
      return "forward:/error";
    }
    enrich(model, req);
    model.addAttribute("status", 403);
    return "error/403";
  }

  @ExceptionHandler(Exception.class)
  public String handleGeneric(Exception ex, HttpServletRequest req, Model model) {
    if (wantsJson(req)) {
      req.setAttribute("javax.servlet.error.status_code", 500);
      return "forward:/error";
    }
    enrich(model, req);
    model.addAttribute("status", 500);
    return "error/500";
  }

  private boolean wantsJson(HttpServletRequest req) {
    String accept = req.getHeader(HttpHeaders.ACCEPT);
    String xr = req.getHeader("X-Requested-With");
    boolean jsonAccept = accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    boolean fetch = xr != null && xr.equalsIgnoreCase("fetch");
    return jsonAccept || fetch;
  }

  private void enrich(Model model, HttpServletRequest req) {
    Object cid = req.getAttribute(CorrelationIdFilter.ATTR);
    model.addAttribute("correlationId", cid != null ? cid.toString() : null);
    model.addAttribute("nowUtc", OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC));

    // evita layout “achar” que é login
    model.addAttribute("pageId", "error");
  }
}