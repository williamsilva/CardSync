package com.cardsync.core.config;

import com.cardsync.domain.service.EmailSenderService;
import com.cardsync.infrastructure.mail.FakeEmailSenderService;
import com.cardsync.infrastructure.mail.SandboxEmailSenderService;
import com.cardsync.infrastructure.mail.SmtpEmailSenderService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
public class EmailConfig {

  private final EmailProperties emailProperties;

  @Bean
  public EmailSenderService sendEmailService() {
    switch (emailProperties.getImpl()) {
      case FAKE:
        return new FakeEmailSenderService();
      case SMTP:
        return new SmtpEmailSenderService();
      case SANDBOX:
        return new SandboxEmailSenderService();
      default:
        return null;
    }
  }

}
