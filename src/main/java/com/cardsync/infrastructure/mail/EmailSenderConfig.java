package com.cardsync.infrastructure.mail;

import com.cardsync.core.config.EmailSettingsService;
import com.cardsync.domain.service.EmailLogService;
import com.cardsync.domain.service.EmailSenderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class EmailSenderConfig {

  @Bean
  RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }

  @Bean
  @ConditionalOnProperty(name = "cardsync.email.impl", havingValue = "brevo")
  EmailSenderService brevoEmailSenderService(
    RestClient.Builder restClientBuilder,
    EmailSettingsService emailSettingsService,
    EmailTemplateProcessor emailTemplateProcessor,
    EmailLogService emailLogService
  ) {
    RestClient restClient = restClientBuilder
      .baseUrl(emailSettingsService.getBrevoBaseUrl())
      .build();

    return new BrevoEmailSenderService(
      restClient,
      emailSettingsService,
      emailLogService,
      emailTemplateProcessor
    );
  }
}
