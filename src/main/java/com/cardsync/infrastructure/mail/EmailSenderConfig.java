package com.cardsync.infrastructure.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class EmailSenderConfig {

  @Bean
  RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }
}
