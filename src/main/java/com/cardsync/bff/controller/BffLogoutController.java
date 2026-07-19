package com.cardsync.bff.controller;

import com.cardsync.bff.service.BffAccessTokenService;
import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.core.security.web.CookieBuilder;
import com.cardsync.core.security.web.CookieProps;
import com.cardsync.core.security.web.SpaRedirectSupport;
import com.cardsync.infrastructure.nimbusauth.NimbusAuthInternalClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BffLogoutController {

  public record LogoutResponse(String logoutUrl) {}

  private final CookieProps cookieProps;
  private final CardsyncSecurityProperties props;
  private final SpaRedirectSupport spaRedirectSupport;
  private final NimbusAuthInternalClient nimbusAuthClient;
  private final OAuth2AuthorizedClientRepository authorizedClientRepository;

  @PostMapping("/bff/logout")
  public ResponseEntity<LogoutResponse> logout(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    // Fallback: se não for possível montar o RP-Initiated Logout do NimbusAuth (ex: sessão
    // já sem OidcUser), pelo menos volta pra SPA com a sessão local já encerrada.
    String logoutUrl = spaRedirectSupport.defaultSpaTarget();

    if (auth != null) {
      // RP-Initiated Logout (OIDC): sem isso, o NimbusAuth mantém sua própria sessão de
      // login válida e o próximo /oauth2/authorize reautentica via SSO silenciosamente -
      // ou seja, o usuário nunca sai de fato, só a sessão do BFF era encerrada.
      if (auth.getPrincipal() instanceof OidcUser oidc && oidc.getIdToken() != null) {
        logoutUrl = props.getIssuer() + "/connect/logout"
          + "?id_token_hint=" + URLEncoder.encode(oidc.getIdToken().getTokenValue(), StandardCharsets.UTF_8)
          + "&post_logout_redirect_uri=" + URLEncoder.encode(spaRedirectSupport.defaultSpaTarget(), StandardCharsets.UTF_8);

        // NÃO revoga a autorização aqui: o /connect/logout do NimbusAuth precisa achar o
        // id_token_hint na tabela de autorizações pra validar o pedido (findByToken) - revogar
        // antes apaga esse registro e o NimbusAuth rejeita com "invalid_token" (400). É o
        // próprio /connect/logout quem encerra a autorização e a sessão de login de lá.
      } else {
        // Sem id_token (ex: sessão sem OidcUser) - não dá pra montar o RP-Initiated Logout,
        // então revoga a autorização diretamente pra não deixar token válido para trás.
        nimbusAuthClient.revokeAuthorization(auth.getName(), BffAccessTokenService.REGISTRATION_ID);
      }

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

    response.addHeader("Set-Cookie", CookieBuilder.clearCookie("SESSION", cookieProps, true, false));
    response.addHeader("Set-Cookie", CookieBuilder.clearCookie("XSRF-TOKEN", cookieProps, false, true));

    return ResponseEntity.ok(new LogoutResponse(logoutUrl));
  }
}
