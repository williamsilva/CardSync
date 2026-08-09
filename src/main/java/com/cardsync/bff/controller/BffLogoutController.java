package com.cardsync.bff.controller;

import com.cardsync.bff.service.BffAccessTokenService;
import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.core.security.web.CookieBuilder;
import com.cardsync.core.security.web.CookieProps;
import com.cardsync.core.security.web.SpaRedirectSupport;
import com.cardsync.infrastructure.nimbusauth.NimbusAuthInternalClient;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
@RequiredArgsConstructor
public class BffLogoutController {

  private static final Logger log = LoggerFactory.getLogger(BffLogoutController.class);

  public record LogoutResponse(String logoutUrl) {}

  private record TokenRefreshResponse(@JsonProperty("id_token") String idToken) {}

  private final CookieProps cookieProps;
  private final CardsyncSecurityProperties props;
  private final SpaRedirectSupport spaRedirectSupport;
  private final NimbusAuthInternalClient nimbusAuthClient;
  private final OAuth2AuthorizedClientRepository authorizedClientRepository;
  private final RestClient.Builder restClientBuilder;

  @PostMapping("/bff/logout")
  public ResponseEntity<LogoutResponse> logout(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    // Fallback: se não for possível montar o RP-Initiated Logout do NimbusAuth (ex: sessão
    // já sem OidcUser), pelo menos volta pra SPA com a sessão local já encerrada.
    String logoutUrl = spaRedirectSupport.defaultSpaTarget();

    if (auth != null) {
      OAuth2AuthorizedClient authorizedClient = authorizedClientRepository.loadAuthorizedClient(
        BffAccessTokenService.REGISTRATION_ID, auth, request);

      // RP-Initiated Logout (OIDC): sem isso, o NimbusAuth mantém sua própria sessão de
      // login válida e o próximo /oauth2/authorize reautentica via SSO silenciosamente -
      // ou seja, o usuário nunca sai de fato, só a sessão do BFF era encerrada.
      String idTokenHint = resolveIdTokenHint(auth, authorizedClient);

      if (idTokenHint != null) {
        logoutUrl = props.getBrowserIssuer() + "/connect/logout"
          + "?id_token_hint=" + URLEncoder.encode(idTokenHint, StandardCharsets.UTF_8)
          + "&post_logout_redirect_uri=" + URLEncoder.encode(spaRedirectSupport.defaultSpaTarget(), StandardCharsets.UTF_8);

        // NÃO revoga a autorização aqui: o /connect/logout do NimbusAuth precisa achar o
        // id_token_hint na tabela de autorizações pra validar o pedido (findByToken) - revogar
        // antes apaga esse registro e o NimbusAuth rejeita com "invalid_token" (400). É o
        // próprio /connect/logout quem encerra a autorização e a sessão de login de lá.
      } else {
        // Sem id_token válido (nem o da sessão, nem foi possível obter um fresco via refresh) -
        // não dá pra montar o RP-Initiated Logout, então revoga a autorização diretamente pra
        // não deixar token válido para trás.
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

    response.addHeader("Set-Cookie", CookieBuilder.clearCookie("CARDSYNC_SESSION", cookieProps, true, false));
    response.addHeader("Set-Cookie", CookieBuilder.clearCookie("CARDSYNC-XSRF-TOKEN", cookieProps, false, true));

    return ResponseEntity.ok(new LogoutResponse(logoutUrl));
  }

  /**
   * O id_token guardado na sessão (OidcUser, fixado no login original) fica órfão rápido: cada
   * refresh silencioso do access_token (automático, a cada poucos minutos - ver access-token-ttl
   * no NimbusAuth) SUBSTITUI o id_token daquela authorization no NimbusAuth, e o /connect/logout
   * rejeita com invalid_token qualquer id_token_hint que não bata com o que está lá agora (ver
   * OidcLogoutAuthenticationProvider no NimbusAuth) - sem um id_token válido, a revogação de
   * tokens no logout nunca chega a rodar (mesmo bug encontrado e corrigido no NimbusFlowServer,
   * arquitetura de BFF idêntica a esta).
   *
   * <p>Por isso pedimos aqui um id_token fresco via grant refresh_token direto contra o token
   * endpoint - fora do OAuth2AuthorizedClientManager padrão, que não expõe id_token (só
   * access/refresh token, id_token é conceito só do login OIDC). Se isso falhar por qualquer
   * motivo (rede, refresh_token já invalidado, etc.), cai pro id_token da sessão mesmo estando
   * possivelmente órfão - pior caso, o RP-Initiated Logout falha como já falhava antes desse
   * ajuste, mas o logout local (sessão do BFF) continua funcionando normalmente.
   */
  private String resolveIdTokenHint(Authentication auth, OAuth2AuthorizedClient authorizedClient) {
    if (authorizedClient != null && authorizedClient.getRefreshToken() != null) {
      String freshIdToken = fetchFreshIdToken(authorizedClient);
      if (freshIdToken != null) {
        return freshIdToken;
      }
    }

    return legacyIdToken(auth);
  }

  private String fetchFreshIdToken(OAuth2AuthorizedClient authorizedClient) {
    ClientRegistration registration = authorizedClient.getClientRegistration();

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "refresh_token");
    body.add("refresh_token", authorizedClient.getRefreshToken().getTokenValue());
    body.add("scope", "openid");

    try {
      TokenRefreshResponse tokenResponse = restClientBuilder.build()
        .post()
        .uri(registration.getProviderDetails().getTokenUri())
        .headers(headers -> headers.setBasicAuth(registration.getClientId(), registration.getClientSecret()))
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(body)
        .retrieve()
        .body(TokenRefreshResponse.class);

      return tokenResponse != null ? tokenResponse.idToken() : null;
    } catch (RestClientException e) {
      log.warn("Não foi possível obter id_token fresco pra logout - RP-Initiated Logout pode falhar: {}",
        e.getMessage());
      return null;
    }
  }

  private String legacyIdToken(Authentication auth) {
    if (auth.getPrincipal() instanceof OidcUser oidc && oidc.getIdToken() != null) {
      return oidc.getIdToken().getTokenValue();
    }
    return null;
  }
}
