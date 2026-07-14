package com.cardsync.core.security;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração de segurança do Cardsync após o split com o NimbusAuth: o Cardsync não
 * emite mais tokens nem faz login local - só valida JWTs emitidos pelo NimbusAuth
 * (issuer/JWKS remotos) e mantém sua própria sessão de BFF (cookies/CORS).
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "cardsync.security")
public class CardsyncSecurityProperties {

  /** Issuer do NimbusAuth (usado para validar o JWT e para resolver o JWKS remoto). */
  @NotNull
  private String issuer;

  @NotNull
  private Cookies cookies;

  /** Tempo de inatividade até expirar a sessão HTTP do BFF (Cardsync, não do NimbusAuth). */
  private Duration sessionTimeout = Duration.ofHours(2);

  private Web web = new Web();
  private ResourceServer resourceServer = new ResourceServer();

  @Data
  public static class ResourceServer {
    private boolean enabled = true;
    private String audience = "cardsync-api";
  }

  @Data
  public static class Web {
    /** Base URL do front-end (SPA) para redirecionamentos do lado servidor (ex: após login). */
    private String spaBaseUrl;
    private List<String> allowedOrigins;
  }

  @Data
  public static class Cookies {
    private String domain;
    private boolean secure;
    private String sameSite;
  }

}
