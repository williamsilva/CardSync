package com.cardsync.core.security.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class SpaRequestMatcher implements RequestMatcher {

  @Override
  public boolean matches(HttpServletRequest req) {
    String accept = req.getHeader("Accept");
    String origin = req.getHeader("Origin");
    String fetchMode = req.getHeader("Sec-Fetch-Mode");   // cors | same-origin | navigate
    String fetchDest = req.getHeader("Sec-Fetch-Dest");   // empty | document | iframe | ...
    String xrw = req.getHeader("X-Requested-With");
    String csrf = req.getHeader("X-XSRF-TOKEN");

    boolean wantsHtml = accept != null && accept.contains("text/html");
    boolean isNavigate = "navigate".equalsIgnoreCase(fetchMode);
    boolean isDocument = "document".equalsIgnoreCase(fetchDest);

    boolean looksLikeSpa =
      (origin != null) ||                                  // dev cross-origin
        "cors".equalsIgnoreCase(fetchMode) ||
        "same-origin".equalsIgnoreCase(fetchMode) ||
        "fetch".equalsIgnoreCase(xrw) ||
        "XMLHttpRequest".equalsIgnoreCase(xrw) ||
        (csrf != null && !csrf.isBlank());

    // ✅ SPA = request “não-navegação” e que não quer HTML
    return looksLikeSpa && !wantsHtml && !isNavigate && !isDocument;
  }
}