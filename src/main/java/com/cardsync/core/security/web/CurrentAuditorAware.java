package com.cardsync.core.security.web;

import com.cardsync.core.security.password.CardSyncUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.cardsync.domain.model.UserEntity;

@Component
@RequiredArgsConstructor
public class CurrentAuditorAware implements AuditorAware<UserEntity> {

  @Override
  public Optional<UserEntity> getCurrentAuditor() {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {

      Object principal = auth.getPrincipal();

      // ✅ formLogin (principal carrega o UUID)
      if (principal instanceof CardSyncUserDetails ud) {
        return stubUser(ud.getId());
      }

      // ✅ resource server / jwt (claim user_id ou sub)
      if (principal instanceof Jwt jwt) {
        UUID id = readUserIdFromJwt(jwt);
        return stubUser(id);
      }
    }

    // ✅ fluxos anônimos: só se você enviar user_id no request (opcional)
    UUID idFromRequest = resolveUserIdFromRequest();
    return stubUser(idFromRequest);
  }

  private Optional<UserEntity> stubUser(UUID id) {
    if (id == null) return Optional.empty();
    UserEntity u = new UserEntity();
    u.setId(id); // apenas id => Hibernate grava FK sem query
    return Optional.of(u);
  }

  private UUID readUserIdFromJwt(Jwt jwt) {
    // preferencial: claim explícita
    String s = safeTrim(jwt.getClaimAsString("user_id"));

    // fallback: se você usa sub como UUID
    if (s == null) s = safeTrim(jwt.getSubject());

    if (s == null) return null;

    try {
      return UUID.fromString(s);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Opcional: se quiser auditar flows anônimos (ex: password expired) com user_id,
   * envie hidden input "userId" no POST e aqui retorna.
   */
  private UUID resolveUserIdFromRequest() {
    ServletRequestAttributes attrs =
      (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) return null;

    HttpServletRequest request = attrs.getRequest();
    if (request == null) return null;

    String s = safeTrim(request.getParameter("userId"));
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