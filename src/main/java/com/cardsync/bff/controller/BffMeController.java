package com.cardsync.bff.controller;

import com.cardsync.core.security.password.CardSyncUserDetails;
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

    // defaults para formLogin
    String iss = null;
    Instant expiresAt = null;
    String userId = null;
    String name = username;

    // 1) Se for formLogin (CardSyncUserDetails), pega id e nome real
    if (auth != null && auth.getPrincipal() instanceof CardSyncUserDetails u) {
      if (u.getId() != null) userId = u.getId().toString();
      if (u.getName() != null && !u.getName().isBlank()) name = u.getName();

      expiresAt = sessionExpiresAt(request);
      return new MeResponse(authenticated, iss, groups, userId, name, username, perms, expiresAt);
    }

    // 2) Se for OIDC, tenta enriquecer com claims do id_token
    if (auth != null && auth.getPrincipal() instanceof OidcUser oidc) {
      OidcIdToken idToken = oidc.getIdToken();
      if (idToken != null) {
        if (idToken.getIssuer() != null) iss = idToken.getIssuer().toString();
        expiresAt = idToken.getExpiresAt();

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

  private static Instant sessionExpiresAt(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) return null;

    int seconds = session.getMaxInactiveInterval(); // timeout por inatividade
    if (seconds <= 0) return null; // 0 ou negativo => sem expiração configurada

    return Instant.now().plusSeconds(seconds);
  }
}