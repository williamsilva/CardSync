package com.cardsync.core.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.*;

@Configuration
@Profile("dev")
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI().info(new Info().title("CardSync API").version("v1"));
  }
}
