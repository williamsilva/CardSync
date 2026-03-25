package com.cardsync.core.security.web;

import com.cardsync.core.security.CardsyncSecurityProperties;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpaRedirectSupport {

  public static final String SESSION_RETURN_TO = "CS_BFF_RETURN_TO";

  private final CardsyncSecurityProperties props;

  public boolean isAllowed(String url) {
    try {
      if (url == null || url.isBlank()) {
        return false;
      }

      if (url.startsWith("/")) {
        return true;
      }

      URI u = URI.create(url);
      String scheme = u.getScheme();
      if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
        return false;
      }

      String origin = u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
      String normalized = normalizeOrigin(origin);

      List<String> allowedOrigins = props.getWeb().getAllowedOrigins();
      return allowedOrigins != null && allowedOrigins.stream()
        .map(this::normalizeOrigin)
        .anyMatch(o -> o.equalsIgnoreCase(normalized));
    } catch (Exception ex) {
      return false;
    }
  }

  public String normalizeForRedirect(String returnTo) {
    if (returnTo == null || returnTo.isBlank()) {
      return null;
    }

    if (returnTo.startsWith("/")) {
      String spaBase = props.getWeb().getSpaBaseUrl();
      if (spaBase == null || spaBase.isBlank()) {
        return null;
      }

      String base = spaBase.endsWith("/") ? spaBase.substring(0, spaBase.length() - 1) : spaBase;
      return base + returnTo;
    }

    return isAllowed(returnTo) ? returnTo : null;
  }

  public void saveReturnTo(HttpSession session, String returnTo) {
    if (session == null || returnTo == null || returnTo.isBlank()) {
      return;
    }

    if (isAllowed(returnTo)) {
      session.setAttribute(SESSION_RETURN_TO, returnTo);
    }
  }

  public String consumeReturnTo(HttpSession session) {
    if (session == null) {
      return null;
    }

    Object value = session.getAttribute(SESSION_RETURN_TO);
    session.removeAttribute(SESSION_RETURN_TO);

    return value instanceof String s && !s.isBlank() ? s : null;
  }

  public String defaultSpaTarget() {
    String fallback = props.getLogin().getDefaultTarget();
    if (fallback == null || fallback.isBlank()) {
      fallback = "/";
    }

    String normalized = normalizeForRedirect(fallback);
    return normalized != null ? normalized : fallback;
  }

  private String normalizeOrigin(String origin) {
    if (origin == null) {
      return null;
    }
    String trimmed = origin.trim();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }
}
