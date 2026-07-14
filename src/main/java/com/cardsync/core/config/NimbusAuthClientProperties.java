package com.cardsync.core.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do cliente HTTP interno usado pelo Cardsync para chamar o NimbusAuth
 * (resolver nome de usuário em /internal/users, revogar autorização em /internal/oauth2/revoke).
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "nimbus-auth")
public class NimbusAuthClientProperties {

  @NotNull
  private String baseUrl;

  @NotNull
  private String internalApiSecret;
}
