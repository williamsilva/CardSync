package com.cardsync.core.security.web;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cardsync.security.cookies")
public class CookieProps {
  private String domain;
  private boolean secure;
  private String sameSite = "Lax";
}
