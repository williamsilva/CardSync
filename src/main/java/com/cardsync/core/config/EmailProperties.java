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

  private String from;
  private String noReply;

  private Impl impl = Impl.FAKE;
  private Sandbox sandbox = new Sandbox();

  public enum Impl {
    SMTP, FAKE, SANDBOX
  }

  @Getter
  @Setter
  public class Sandbox {
    private String to;
  }
}
