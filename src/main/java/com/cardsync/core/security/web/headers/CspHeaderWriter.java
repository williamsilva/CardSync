package com.cardsync.core.security.web.headers;

import com.cardsync.core.security.web.nonce.CspNonceFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.security.web.header.HeaderWriter;

public final class CspHeaderWriter implements HeaderWriter {

  private final String template;

  public CspHeaderWriter(String template) {
    this.template = Objects.requireNonNull(template, "template");
  }

  @Override
  public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
    Object nonce = request.getAttribute(CspNonceFilter.ATTR_NONCE);
    if (nonce == null) {
      // Sem nonce => cai para política sem nonce (ou não escreve)
      response.setHeader("Content-Security-Policy", template.replace("'nonce-{nonce}'", "'self'"));
      return;
    }

    String csp = template.replace("{nonce}", nonce.toString());
    response.setHeader("Content-Security-Policy", csp);
  }
}
