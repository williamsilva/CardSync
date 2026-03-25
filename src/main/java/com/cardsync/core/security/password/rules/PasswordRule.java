package com.cardsync.core.security.password.rules;

public interface PasswordRule {
  /**
   * Chave única para ligar na property cardsync.security.password.rules.<key>
   * Ex: "digit", "upper", "symbol"
   */
  String key();

  /**
   * i18n key para UI/erros (sem hardcode no HTML)
   * Ex: "password.rule.digit"
   */
  String messageKey();

  /**
   * Retorna true se a senha cumpre a regra.
   */
  boolean matches(String rawPassword);

  /**
   * Versão com contexto (ex: username). Regras que não precisam podem
   * ignorar e usar o default.
   */
  default boolean matches(String rawPassword, PasswordRuleContext ctx) {
    return matches(rawPassword);
  }

  default boolean enabledByDefault() {
    return false;
  }
}
