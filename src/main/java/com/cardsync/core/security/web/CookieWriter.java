package com.cardsync.core.security.web;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Escrita de cookies com SameSite (via header Set-Cookie).
 * Usado no baseline para "remember last username" (CS_LAST_USERNAME).
 */
public final class CookieWriter {

  private CookieWriter() {}

  public static final String LAST_USERNAME_COOKIE = "CS_LAST_USERNAME";

  public static void setLastUsername(HttpServletResponse response, CookieProps props, String username) {
    if (response == null || props == null) return;
    String u = (username == null) ? "" : username.trim();
    if (u.isBlank()) return;

    String value = URLEncoder.encode(u, StandardCharsets.UTF_8);

    // 90 dias
    long maxAgeSeconds = 60L * 60L * 24L * 90L;

    StringBuilder sb = new StringBuilder();
    sb.append(LAST_USERNAME_COOKIE).append("=").append(value);
    sb.append("; Path=/");
    sb.append("; Max-Age=").append(maxAgeSeconds);
    sb.append("; SameSite=").append(props.getSameSite() == null ? "Lax" : props.getSameSite());

    if (props.getDomain() != null && !props.getDomain().isBlank()) {
      sb.append("; Domain=").append(props.getDomain().trim());
    }
    if (props.isSecure()) {
      sb.append("; Secure");
    }

    // não é HttpOnly por design: é apenas conveniência de preenchimento (não é credencial)
    response.addHeader("Set-Cookie", sb.toString());
  }
}
