package com.cardsync.core.security.password.rules;

import org.springframework.stereotype.Component;

@Component
public class SymbolRule implements PasswordRule {
  @Override public String key() { return "symbol"; }
  @Override public String messageKey() { return "password.rule.symbol"; }
  @Override public boolean matches(String rawPassword) {
    if (rawPassword == null) return false;
    // Qualquer caractere que não seja letra/dígito (ajuste se quiser lista permitida)
    return rawPassword.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
  }
  @Override public boolean enabledByDefault() { return true; }
}
