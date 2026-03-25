package com.cardsync.core.security.web;

import com.cardsync.core.security.CardsyncSecurityProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CardsyncSecurityProperties props) {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(props.getWeb().getAllowedOrigins());
    cfg.setAllowCredentials(true);

    // headers usados pelo baseline (CSRF, JSON, etc.)
    cfg.setMaxAge(3600L);
    cfg.setAllowedHeaders(List.of("Content-Type", "Accept", "Authorization", "X-XSRF-TOKEN", "X-Requested-With"));
    cfg.setExposedHeaders(List.of("Authorization","Content-Type","Accept","X-Requested-With","X-XSRF-TOKEN", "Location"));
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    // aplica para BFF e API (o browser só deve chamar BFF, mas deixamos consistente)
    source.registerCorsConfiguration("/bff/csrf", cfg);
    source.registerCorsConfiguration("/bff/**", cfg);
    source.registerCorsConfiguration("/api/**", cfg);

    // ✅ ESSENCIAL: endpoints do OAuth2 client/authorization
    source.registerCorsConfiguration("/oauth2/**", cfg);
    source.registerCorsConfiguration("/login/oauth2/**", cfg);
    return source;
  }
}
