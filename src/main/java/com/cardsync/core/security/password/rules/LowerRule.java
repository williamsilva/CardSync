package com.cardsync.core.security.password.rules;

import org.springframework.stereotype.Component;

@Component
public class LowerRule implements PasswordRule {
  @Override public String key() { return "lower"; }
  @Override public String messageKey() { return "password.rule.lower"; }
  @Override public boolean matches(String rawPassword) {
    if (rawPassword == null) return false;
    return rawPassword.chars().anyMatch(Character::isLowerCase);
  }
}
