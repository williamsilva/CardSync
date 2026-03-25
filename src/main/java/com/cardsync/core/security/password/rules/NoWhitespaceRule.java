package com.cardsync.core.security.password.rules;

import org.springframework.stereotype.Component;

@Component
public class NoWhitespaceRule implements PasswordRule {
  @Override public String key() { return "noWhitespace"; }
  @Override public String messageKey() { return "password.rule.noWhitespace"; }
  @Override public boolean matches(String rawPassword) {
    if (rawPassword == null) return false;
    return rawPassword.chars().noneMatch(Character::isWhitespace);
  }
}
