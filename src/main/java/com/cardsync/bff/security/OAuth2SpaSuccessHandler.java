package com.cardsync.bff.security;

import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.core.security.web.SpaRedirectSupport;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * SuccessHandler para SPA+BFF.
 *
 * Prioridade:
 * 1) redirect preparado pela SPA e salvo na sessão
 * 2) SavedRequest (quando existir)
 * 3) default-target
 */
@RequiredArgsConstructor
public class OAuth2SpaSuccessHandler implements AuthenticationSuccessHandler {

  private final CardsyncSecurityProperties props;
  private final RequestCache requestCache;
  private final SpaRedirectSupport spaRedirectSupport;

  @Override
  public void onAuthenticationSuccess(
    HttpServletRequest request,
    HttpServletResponse response,
    Authentication authentication
  ) throws IOException, ServletException {

    String preparedReturnTo = spaRedirectSupport.normalizeForRedirect(
      spaRedirectSupport.consumeReturnTo(request.getSession(false))
    );

    if (preparedReturnTo != null) {
      clearSavedRequest(request, response);
      response.sendRedirect(preparedReturnTo);
      return;
    }

    SavedRequest saved = requestCache.getRequest(request, response);
    if (saved != null) {
      requestCache.removeRequest(request, response);
      response.sendRedirect(saved.getRedirectUrl());
      return;
    }

    String fallback = props.getLogin().getDefaultTarget();
    if (fallback == null || fallback.isBlank()) {
      fallback = "/";
    }

    String normalizedFallback = spaRedirectSupport.normalizeForRedirect(fallback);
    response.sendRedirect(normalizedFallback != null ? normalizedFallback : fallback);
  }

  private void clearSavedRequest(HttpServletRequest request, HttpServletResponse response) {
    SavedRequest saved = requestCache.getRequest(request, response);
    if (saved != null) {
      requestCache.removeRequest(request, response);
    }
  }
}