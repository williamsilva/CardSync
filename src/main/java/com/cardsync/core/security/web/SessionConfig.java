package com.cardsync.core.security.web;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

@Configuration
public class SessionConfig {

  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  @Bean
  org.springframework.session.config.SessionRepositoryCustomizer<JdbcIndexedSessionRepository> springSessionTimeoutCustomizer() {
    return repository -> repository.setDefaultMaxInactiveInterval(Duration.ofMinutes(30));
  }
}