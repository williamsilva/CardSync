package com.cardsync.core.security.password.rules;

/**
 * Contexto para validação de regras de senha.
 * Hoje suporta username, mas pode evoluir (ex: nome, doc, etc.).
 */
public record PasswordRuleContext(String username) {
  public static PasswordRuleContext of(String username) {
    return new PasswordRuleContext(username);
  }
}
