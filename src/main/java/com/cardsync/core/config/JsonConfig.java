package com.cardsync.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JsonConfig {

  @Bean
  @Primary
  public ObjectMapper cleanHttpObjectMapper() {
    ObjectMapper mapper = JsonMapper.builder().build();
    return mapper;
  }
}