package com.cardsync.core.security.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * O usuario autenticado no Cardsync sempre vem do NimbusAuth (Jwt no Resource Server,
 * OidcUser no BFF) - nunca ha um UserEntity local. O auditor eh so o UUID do usuario.
 */
@Component
public class CurrentAuditorAware implements AuditorAware<UUID> {

  @Override
  public Optional<UUID> getCurrentAuditor() {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {

      Object principal = auth.getPrincipal();

      if (principal instanceof Jwt jwt) {
        return Optional.ofNullable(readUserIdFromJwt(jwt));
      }

      if (principal instanceof OidcUser oidc) {
        return Optional.ofNullable(readUserIdFromOidc(oidc));
      }
    }

    // fluxos anônimos: só se você enviar user_id no request (opcional)
    return Optional.ofNullable(resolveUserIdFromRequest());
  }

  private UUID readUserIdFromJwt(Jwt jwt) {
    String s = safeTrim(jwt.getClaimAsString("userId"));
    if (s == null) s = safeTrim(jwt.getClaimAsString("user_id"));
    if (s == null) s = safeTrim(jwt.getSubject());
    return parseUuid(s);
  }

  private UUID readUserIdFromOidc(OidcUser oidc) {
    String s = safeTrim(oidc.getClaimAsString("userId"));
    if (s == null) s = safeTrim(oidc.getClaimAsString("user_id"));
    if (s == null) s = safeTrim(oidc.getSubject());
    return parseUuid(s);
  }

  private UUID resolveUserIdFromRequest() {
    ServletRequestAttributes attrs =
      (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) return null;

    HttpServletRequest request = attrs.getRequest();
    if (request == null) return null;

    return parseUuid(safeTrim(request.getParameter("userId")));
  }

  private UUID parseUuid(String s) {
    if (s == null) return null;
    try {
      return UUID.fromString(s);
    } catch (Exception e) {
      return null;
    }
  }

  private String safeTrim(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isBlank() ? null : t;
  }
}
