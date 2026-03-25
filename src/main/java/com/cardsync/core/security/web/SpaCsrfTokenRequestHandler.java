package com.cardsync.core.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import java.util.function.Supplier;

/**
 * SPA friendly CSRF handler:
 * - Se veio token via Header (X-XSRF-TOKEN), aceita o valor "cru" (lido do cookie no Angular).
 * - Caso contrário, mantém o comportamento padrão (XOR masking) para páginas/form.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

  private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    this.delegate.handle(request, response, csrfToken);
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    String headerValue = request.getHeader(csrfToken.getHeaderName());
    if (headerValue != null && !headerValue.isBlank()) {
      return headerValue; // ✅ valor cru do header (SPA)
    }
    return this.delegate.resolveCsrfTokenValue(request, csrfToken);
  }
}
