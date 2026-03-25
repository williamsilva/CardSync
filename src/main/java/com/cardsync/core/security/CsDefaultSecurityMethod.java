package com.cardsync.core.security;

import com.cardsync.core.security.password.CardSyncUserDetails;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

public abstract class CsDefaultSecurityMethod extends CsPermissions {

  public Authentication getAuthentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }

  public boolean isAuthenticated() {
    Authentication authentication = getAuthentication();
    return authentication != null
      && authentication.isAuthenticated()
      && !"anonymousUser".equals(authentication.getPrincipal());
  }

  public boolean hasAuthority(String authorityName) {
    Authentication authentication = getAuthentication();
    if (authentication == null) {
      return false;
    }

    return authentication.getAuthorities().stream()
      .map(GrantedAuthority::getAuthority)
      .filter(Objects::nonNull)
      .anyMatch(authority ->
        Objects.equals(authority, ROLE_SUPPORT)
          || Objects.equals(authority, authorityName)
      );
  }

  public boolean isAuthenticatedUserEqual(UUID userId) {
    if (userId == null) {
      return false;
    }

    return userId.equals(getCurrentUserId().orElse(null));
  }

  public Optional<UUID> getCurrentUserId() {
    Authentication authentication = getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }

    Object principal = authentication.getPrincipal();

    if (principal instanceof CardSyncUserDetails ud) {
      return Optional.ofNullable(ud.getId());
    }

    if (principal instanceof Jwt jwt) {
      return Optional.ofNullable(readUserIdFromJwt(jwt));
    }

    if (principal instanceof OidcUser oidc) {
      return Optional.ofNullable(readUserIdFromOidc(oidc));
    }

    return Optional.empty();
  }

  public UUID getCurrentUserIdOrNull() {
    return getCurrentUserId().orElse(null);
  }

  public Optional<String> getCurrentUsername() {
    Authentication authentication = getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }

    Object principal = authentication.getPrincipal();

    if (principal instanceof CardSyncUserDetails ud) {
      return Optional.ofNullable(safeTrim(ud.getUsername()));
    }

    if (principal instanceof Jwt jwt) {
      return Optional.ofNullable(readUsernameFromJwt(jwt));
    }

    if (principal instanceof OidcUser oidc) {
      return Optional.ofNullable(readUsernameFromOidc(oidc));
    }

    Object name = authentication.getName();
    if (name instanceof String value) {
      return Optional.ofNullable(safeTrim(value));
    }

    return Optional.empty();
  }

  public String getCurrentUsernameOrNull() {
    return getCurrentUsername().orElse(null);
  }

  public boolean hasAuthenticatedUser() {
    return getCurrentUserId().isPresent();
  }

  public Optional<CardSyncUserDetails> getCurrentUserDetails() {
    Authentication authentication = getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }

    Object principal = authentication.getPrincipal();
    if (principal instanceof CardSyncUserDetails ud) {
      return Optional.of(ud);
    }

    return Optional.empty();
  }

  private UUID readUserIdFromJwt(Jwt jwt) {
    String value = safeTrim(jwt.getClaimAsString("user_id"));

    if (value == null) {
      value = safeTrim(jwt.getClaimAsString("userId"));
    }

    if (value == null) {
      value = safeTrim(jwt.getSubject());
    }

    return parseUuid(value);
  }

  private UUID readUserIdFromOidc(OidcUser oidc) {
    String value = safeTrim(oidc.getClaimAsString("userId"));

    if (value == null) {
      value = safeTrim(oidc.getClaimAsString("user_id"));
    }

    if (value == null) {
      value = safeTrim(oidc.getSubject());
    }

    return parseUuid(value);
  }

  private String readUsernameFromJwt(Jwt jwt) {
    String value = safeTrim(jwt.getClaimAsString("preferred_username"));

    if (value == null) {
      value = safeTrim(jwt.getClaimAsString("username"));
    }

    if (value == null) {
      value = safeTrim(jwt.getClaimAsString("email"));
    }

    if (value == null) {
      value = safeTrim(jwt.getSubject());
    }

    return value;
  }

  private String readUsernameFromOidc(OidcUser oidc) {
    String value = safeTrim(oidc.getClaimAsString("preferred_username"));

    if (value == null) {
      value = safeTrim(oidc.getClaimAsString("username"));
    }

    if (value == null) {
      value = safeTrim(oidc.getClaimAsString("email"));
    }

    if (value == null) {
      value = safeTrim(oidc.getSubject());
    }

    return value;
  }

  private UUID parseUuid(String value) {
    if (value == null) {
      return null;
    }

    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private String safeTrim(String value) {
    if (value == null) {
      return null;
    }

    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }
}