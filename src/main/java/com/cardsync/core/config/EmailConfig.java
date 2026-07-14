package com.cardsync.core.config;

import com.cardsync.domain.service.EmailLogService;
import com.cardsync.domain.service.EmailSenderService;
import com.cardsync.infrastructure.mail.BrevoEmailSenderService;
import com.cardsync.infrastructure.mail.EmailTemplateProcessor;
import com.cardsync.infrastructure.mail.FakeEmailSenderService;
import com.cardsync.infrastructure.mail.SmtpEmailSenderService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
@AllArgsConstructor
public class EmailConfig {

  private final EmailSettingsService emailSettingsService;
  private final EmailLogService emailLogService;
  private final RestClient.Builder restClientBuilder;
  private final EmailTemplateProcessor emailTemplateProcessor;

  @Bean
  @Primary
  public EmailSenderService sendEmailService() {
    return switch (emailSettingsService.getImpl()) {
      case FAKE -> new FakeEmailSenderService();
      case SMTP -> new SmtpEmailSenderService();
      case BREVO -> new BrevoEmailSenderService(
        restClientBuilder
          .baseUrl(emailSettingsService.getBrevoBaseUrl())
          .build(),
        emailSettingsService,
        emailLogService,
        emailTemplateProcessor
      );
    };
  }

}
