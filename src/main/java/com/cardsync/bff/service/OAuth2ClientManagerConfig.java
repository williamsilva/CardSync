package com.cardsync.bff.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

@Configuration
public class OAuth2ClientManagerConfig {

  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(
    ClientRegistrationRepository registrations,
    OAuth2AuthorizedClientRepository authorizedClientRepository
  ) {
    DefaultOAuth2AuthorizedClientManager manager =
      new DefaultOAuth2AuthorizedClientManager(registrations, authorizedClientRepository);

    OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
      .authorizationCode()
      .refreshToken()
      .build();

    manager.setAuthorizedClientProvider(provider);
    return manager;
  }
}
