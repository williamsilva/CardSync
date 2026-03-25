package com.cardsync.bff.controller;

import com.cardsync.bff.service.BffAccessTokenService;
import com.cardsync.core.security.authserver.AuthorizationRevocationService;
import com.cardsync.core.security.web.CookieBuilder;
import com.cardsync.core.security.web.CookieProps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BffLogoutController {

  private final CookieProps cookieProps;
  private final AuthorizationRevocationService revocationService;
  private final OAuth2AuthorizedClientRepository authorizedClientRepository;

  @PostMapping("/bff/logout")
  public ResponseEntity<Void> logout(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    if (auth != null) {
      // Revoke chain: invalida todas as autorizações do usuário para o client
      revocationService.revokeAllForPrincipalAndClient(auth.getName(), BffAccessTokenService.REGISTRATION_ID);

      // Remove client da sessão (tokens server-side)
      authorizedClientRepository.removeAuthorizedClient(
        BffAccessTokenService.REGISTRATION_ID, auth, request, response
      );
    }

    // Invalida sessão
    var session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }

    response.addHeader("Set-Cookie", CookieBuilder.clearCookie("JSESSIONID", cookieProps, true));
    response.addHeader("Set-Cookie", CookieBuilder.clearCookie("XSRF-TOKEN", cookieProps, false));

    return ResponseEntity.noContent().build();
  }
}
