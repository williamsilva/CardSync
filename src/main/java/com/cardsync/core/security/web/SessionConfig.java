package com.cardsync.core.security.web;

import com.cardsync.core.security.CardsyncSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

@Configuration
@RequiredArgsConstructor
public class SessionConfig {

  private final CardsyncSecurityProperties props;

  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  @Bean
  org.springframework.session.config.SessionRepositoryCustomizer<JdbcIndexedSessionRepository> springSessionTimeoutCustomizer() {
    return repository -> repository.setDefaultMaxInactiveInterval(
      props.getSessionTimeout()
    );
  }
}