package com.cardsync.core.security.authserver;

import lombok.Data;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class AuthServerProperties {

  private boolean devGenerateKey;
  private Client client = new Client();
  private Keystore keystore = new Keystore();

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
    private String clientSecret;
    private String redirectUriDev;
    private String redirectUriProd;
  }
}
