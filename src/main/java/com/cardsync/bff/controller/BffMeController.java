package com.cardsync.bff.controller;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BffMeController {

  public record MeResponse(
    boolean authenticated,
    String iss,
    List<String> groups,
    String userId,
    String name,
    String username,
    List<String> authorities,
    Instant expiresAt
  ) {}

  @GetMapping("/bff/me")
  public MeResponse me(Authentication auth, HttpServletRequest request) {
    boolean authenticated = auth != null && auth.isAuthenticated();

    // username sempre disponível em Spring Security
    String username = auth != null ? auth.getName() : null;

    // autoridades "cruas" (ROLE_/PERM_/etc)
    List<String> raw = auth == null
      ? List.of()
      : auth.getAuthorities().stream()
      .map(GrantedAuthority::getAuthority)
      .toList();

    // grupos e permissões derivados de ROLE_/PERM_
    List<String> groups = raw.stream()
      .filter(a -> a != null && a.startsWith("ROLE_"))
      .map(a -> a.substring("ROLE_".length()))
      .distinct()
      .toList();

    List<String> perms = raw.stream()
      .filter(a -> a != null && a.startsWith("PERM_"))
      .map(a -> a.substring("PERM_".length()))
      .distinct()
      .toList();

    // defaults
    String iss = null;
    String userId = null;
    String name = username;

    // Expiração reportada ao SPA é a da sessão HTTP do BFF (renovada a cada request),
    // não a do id_token (fixa desde o login) - senão o "renew" do front nunca avança
    // e a sessão parece expirar mesmo com o cookie/sessão ainda válidos.
    Instant expiresAt = null;
    HttpSession session = request.getSession(false);
    if (authenticated && session != null) {
      expiresAt = Instant.ofEpochMilli(session.getLastAccessedTime())
        .plusSeconds(session.getMaxInactiveInterval());
    }

    // Se for OIDC (BFF via oauth2Login contra o NimbusAuth), enriquece com claims do id_token
    if (auth != null && auth.getPrincipal() instanceof OidcUser oidc) {
      OidcIdToken idToken = oidc.getIdToken();
      if (idToken != null) {
        if (idToken.getIssuer() != null) iss = idToken.getIssuer().toString();

        Object userIdRaw = idToken.getClaim("userId");
        if (userIdRaw != null) userId = String.valueOf(userIdRaw);

        String nameClaim = idToken.getClaimAsString("name");
        if (nameClaim != null && !nameClaim.isBlank()) name = nameClaim;

        String usernameClaim = idToken.getClaimAsString("username");
        if (usernameClaim != null && !usernameClaim.isBlank()) username = usernameClaim;

      }

      return new MeResponse(authenticated, iss, groups, userId, name, username, perms, expiresAt);
    }

    // fallback genérico
    return new MeResponse(authenticated, iss, groups, userId, name, username, perms, expiresAt);
  }
}