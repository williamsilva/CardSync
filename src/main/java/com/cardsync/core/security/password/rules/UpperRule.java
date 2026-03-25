package com.cardsync.core.security.password.rules;

import org.springframework.stereotype.Component;

@Component
public class UpperRule implements PasswordRule {
  @Override public String key() { return "upper"; }
  @Override public String messageKey() { return "password.rule.upper"; }
  @Override public boolean matches(String rawPassword) {
    if (rawPassword == null) return false;
    return rawPassword.chars().anyMatch(Character::isUpperCase);
  }
  @Override public boolean enabledByDefault() { return true; }
}
