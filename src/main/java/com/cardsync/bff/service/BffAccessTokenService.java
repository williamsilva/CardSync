package com.cardsync.bff.service;

import com.cardsync.core.security.authserver.AuthorizationRevocationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BffAccessTokenService {

  public static final String REGISTRATION_ID = "cardsync-bff";

  private final OAuth2AuthorizedClientManager clientManager;
  private final AuthorizationRevocationService revocationService;
  private final OAuth2AuthorizedClientRepository authorizedClientRepository;

  /**
   * Resolve um access token válido. Se precisar, faz refresh server-side.
   * Se o refresh falhar (ex: invalid_grant), revoga a cadeia no AS e força re-login.
   */
  public String getValidAccessTokenOrRevoke(
    Authentication authentication,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    if (!(authentication instanceof OAuth2AuthenticationToken oauth2Auth)) {
      throw new IllegalStateException("Não autenticado via oauth2Login.");
    }

    try {
      OAuth2AuthorizeRequest req = OAuth2AuthorizeRequest
        .withClientRegistrationId(REGISTRATION_ID)
        .principal(oauth2Auth)
        .build();

      OAuth2AuthorizedClient client = clientManager.authorize(req);
      if (client == null || client.getAccessToken() == null) {
        throw new IllegalStateException("OAuth2AuthorizedClient nulo/sem access token.");
      }
      return client.getAccessToken().getTokenValue();

    } catch (OAuth2AuthorizationException ex) {
      // RFC 9700: em caso de reuse/refresh inválido, revogar cadeia e forçar novo login
      revokeChainAndClearSession(authentication, request, response);
      // Re-throw como "não autenticado" para o caller decidir redirecionar/401
      throw ex;
    }
  }

  private void revokeChainAndClearSession(
    Authentication authentication,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    String principal = authentication.getName();

    // Revoke chain no AS para este principal + client
    revocationService.revokeAllForPrincipalAndClient(principal, REGISTRATION_ID);

    // Remove authorized client (session)
    authorizedClientRepository.removeAuthorizedClient(
      REGISTRATION_ID, authentication, request, response
    );

    // Invalida sessão (força re-login)
    var session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }

    // Limpa cookies básicos (DEV defaults)
    response.addHeader("Set-Cookie", "JSESSIONID=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
    response.addHeader("Set-Cookie", "XSRF-TOKEN=; Path=/; Max-Age=0; SameSite=Lax");
  }
}
