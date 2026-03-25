package com.cardsync.core.security.authserver;

import com.cardsync.core.security.CardsyncSecurityProperties;
import java.time.Duration;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

@Configuration
@RequiredArgsConstructor
public class RegisteredClientBootstrap {

  private final CardsyncSecurityProperties props;

  /**
   * Bootstrap do client cardsync-bff (confidential + refresh token + rotation).
   */
  @Bean
  ApplicationRunner bootstrapRegisteredClient(RegisteredClientRepository repo,  PasswordEncoder encoder) {
    return args -> {
      String clientId = props.getAuthserver().getClient().getClientId();
      RegisteredClient existing = repo.findByClientId(clientId);
      if (existing!=null) {
        return;
      }

      String secret = props.getAuthserver().getClient().getClientSecret();

      // Redirect URIs fixos e exatos (DEV/PROD)
      String redirectDev = props.getAuthserver().getClient().getRedirectUriDev();
      String redirectProd = props.getAuthserver().getClient().getRedirectUriProd();

      TokenSettings tokenSettings = TokenSettings.builder()
        .accessTokenTimeToLive(Duration.ofMinutes(10))
        // Refresh token server-side (baseline). Rotation a cada uso:
        .reuseRefreshTokens(false)
        .refreshTokenTimeToLive(Duration.ofDays(30))
        .build();

      ClientSettings clientSettings = ClientSettings.builder()
        .requireAuthorizationConsent(false)
        .build();

      RegisteredClient bffClient = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId(clientId)
        .clientSecret(encoder.encode(secret))
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri(redirectDev)
        .redirectUri(redirectProd)
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .tokenSettings(tokenSettings)
        .clientSettings(clientSettings)
        .build();

      repo.save(bffClient);
    };
  }
}
