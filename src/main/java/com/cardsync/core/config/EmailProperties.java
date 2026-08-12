package com.cardsync.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties("cardsync.email")
public class EmailProperties {

  private String fromName;
  private String fromEmail;

  // Base pra montar URLs absolutas de assets referenciados nos e-mails (logo) - não depende de
  // request HTTP nenhuma (diferente de web.spa-base-url, que é da SPA, não do próprio
  // CardsyncServer). Mesmo padrão de password.tokens.public-base-url no NimbusAuth.
  private String publicBaseUrl = "https://api.cardsync.com.br";

  private Impl impl = Impl.FAKE;
  private final Brevo brevo = new Brevo();
  private final Smtp smtp = new Smtp();

  @Getter
  @Setter
  public static class Brevo {
    private String apiKey;
    private String baseUrl;
    private Integer port = 587;
    private String username;
  }

  @Getter
  @Setter
  public static class Smtp {
    private String host;
    private Integer port = 587;
    private String username;
    private String password;
    private Boolean auth = true;
    private Boolean starttls = false;
    private Boolean ssl = false;
  }

  // BREVO -> API_KEY: mesmo rename feito no NimbusAuth (ver com.nimbus.auth.core.config.
  // EmailProperties) - nome genérico pro modo "chave de API", não acoplado ao fornecedor Brevo.
  public enum Impl {
    SMTP, FAKE, API_KEY
  }
}
