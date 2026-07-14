package com.cardsync.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JsonConfig {

  /**
   * Não é @Primary: o ObjectMapper default do app é o autoconfigurado pelo Spring Boot
   * (jacksonJsonMapper, com os módulos padrão - ex: JavaTimeModule). Este é só para quem
   * precisar explicitamente de um mapper "limpo", via @Qualifier("cleanHttpObjectMapper").
   */
  @Bean
  public ObjectMapper cleanHttpObjectMapper() {
    return JsonMapper.builder().build();
  }
}