package com.cardsync.core.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

  @Bean
  public ModelMapper modelMapper() {
    ModelMapper mm = new ModelMapper();
    mm.getConfiguration()
      .setSkipNullEnabled(true);
    return mm;
  }
}
