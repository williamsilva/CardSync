package com.cardsync.core.security.authserver;

import java.time.Duration;
import lombok.Data;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class AuthServerProperties {

  private boolean devGenerateKey;
  private Client client = new Client();
  private Keystore keystore = new Keystore();
  private Token token = new Token();

  @Data
  public static class Keystore {
    private String location;
    private String password;
    private String keyAlias;
    private String keyPassword;
  }

  @Data
  public static class Client {
    private String clientId;
    private String redirectUri;
    private String clientSecret;
  }

  @Data
  public static class Token {
    /** Tempo máximo de inatividade antes de expirar a sessão HTTP. */
    private Duration sessionTimeout = Duration.ofMinutes(30);
    /** Validade do access token JWT. */
    private Duration accessTokenTtl = Duration.ofMinutes(10);
    /** Validade do refresh token. */
    private Duration refreshTokenTtl = Duration.ofDays(30);
  }
}
