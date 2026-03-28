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

  private Impl impl = Impl.FAKE;
  private final Brevo brevo = new Brevo();

  @Getter
  @Setter
  public static class Brevo {
    private String apiKey;
    private String baseUrl;
  }

  public enum Impl {
    SMTP, FAKE, BREVO
  }

}
