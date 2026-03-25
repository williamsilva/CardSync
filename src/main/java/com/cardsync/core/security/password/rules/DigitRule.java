package com.cardsync.core.security.password.rules;

import org.springframework.stereotype.Component;

@Component
public class DigitRule implements PasswordRule {
  @Override public String key() { return "digit"; }
  @Override public String messageKey() { return "password.rule.digit"; }
  @Override public boolean matches(String rawPassword) {
    if (rawPassword == null) return false;
    return rawPassword.chars().anyMatch(Character::isDigit);
  }
  @Override public boolean enabledByDefault() { return true; }
}
