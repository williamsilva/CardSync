package com.cardsync.core.security.password;

import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.core.security.password.dtos.PasswordRulesViewModel;
import com.cardsync.core.security.password.rules.PasswordRule;
import com.cardsync.core.security.password.rules.PasswordRuleContext;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.model.enums.PasswordRuleCode;
import com.cardsync.domain.repository.PasswordHistoryRepository;
import com.cardsync.domain.repository.UserRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

  private final UserRepository userRepo;
  private final PasswordEncoder encoder;
  private final MessageSource messageSource;
  private final CardsyncSecurityProperties props;
  private final List<PasswordRule> availableRules;
  private final PasswordHistoryRepository historyRepo;

  /**
   * Lista inicial em PENDING (para o checklist). Server-driven: só devolve o que está habilitado.
   */
  public PasswordRulesViewModel policyPending() {
    int minLen = props.getPassword().getMinLength();
    int historySize = props.getPassword().getHistorySize();

    Set<String> enabled = enabledCodes();
    Set<String> pending = new LinkedHashSet<>(enabled);

    var rules = PasswordRuleViews.fromCodes(
      enabled,
      Set.of(),
      pending,
      minLen,
      historySize,
      labels()
    );

    return new PasswordRulesViewModel(false, minLen, historySize, rules);
  }

  /**
   * Check completo (server-driven). username é opcional e não deve ser exibido.
   */
  public PasswordRulesViewModel check(String rawPassword, String confirmPassword, String username) {
    int minLen = props.getPassword().getMinLength();
    int historySize = props.getPassword().getHistorySize();

    Set<String> enabled = enabledCodes();
    Set<String> failed = new LinkedHashSet<>();
    Set<String> pending = new LinkedHashSet<>();

    String pwd = rawPassword == null ? "" : rawPassword;
    String conf = confirmPassword == null ? "" : confirmPassword;

    // sem senha => tudo PENDING (não "vermelhar" sem interação)
    if (pwd.isBlank()) {
      pending.addAll(enabled);
      var rules = PasswordRuleViews.fromCodes(enabled, failed, pending, minLen, historySize, labels());
      return new PasswordRulesViewModel(false, minLen, historySize, rules);
    }

    // 1) tamanho
    if (pwd.length() < minLen) {
      failed.add(PasswordRuleCode.PASSWORD_TOO_SHORT.name());
    }

    // 2) regras declaradas
    var ctx = PasswordRuleContext.of(username);
    for (PasswordRule rule : availableRules) {
      PasswordRuleCode code = codeForRuleKey(rule.key());
      if (code == null) continue;
      if (!enabled.contains(code.name())) continue;

      // regra noUsername depende de username
      if (code == PasswordRuleCode.PASSWORD_CONTAINS_USERNAME && (username == null || username.isBlank())) {
        pending.add(code.name());
        continue;
      }

      boolean ok = rule.matches(pwd, ctx);
      if (!ok) failed.add(code.name());
    }

    // 3) match
    if (enabled.contains(PasswordRuleCode.PASSWORD_NOT_MATCH.name())) {
      if (conf.isBlank()) {
        pending.add(PasswordRuleCode.PASSWORD_NOT_MATCH.name());
      } else if (!pwd.equals(conf)) {
        failed.add(PasswordRuleCode.PASSWORD_NOT_MATCH.name());
      }
    }

    // 4) dependentes do usuário (sem vazar existência)
    UserEntity user = resolveUser(username);

    if (enabled.contains(PasswordRuleCode.PASSWORD_SAME_AS_CURRENT.name())) {
      if (user == null) {
        // não vazar existência; deixa pendente até termos um usuário resolvido
        pending.add(PasswordRuleCode.PASSWORD_SAME_AS_CURRENT.name());
      } else if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
        if (encoder.matches(pwd, user.getPasswordHash())) {
          failed.add(PasswordRuleCode.PASSWORD_SAME_AS_CURRENT.name());
        }
      }
    }

    if (enabled.contains(PasswordRuleCode.PASSWORD_REUSED_FROM_HISTORY.name())) {
      int n = Math.max(0, historySize);
      if (n > 0) {
        if (user == null) {
          pending.add(PasswordRuleCode.PASSWORD_REUSED_FROM_HISTORY.name());
        } else {
          var last = historyRepo.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, n));
          boolean reused = last.stream().anyMatch(ph -> ph != null
            && ph.getPasswordHash() != null
            && !ph.getPasswordHash().isBlank()
            && encoder.matches(pwd, ph.getPasswordHash()));
          if (reused) failed.add(PasswordRuleCode.PASSWORD_REUSED_FROM_HISTORY.name());
        }
      }
    }

    var rules = PasswordRuleViews.fromCodes(enabled, failed, pending, minLen, historySize, labels());
    boolean ok = rules.stream().allMatch(r -> "OK".equals(r.state()));
    return new PasswordRulesViewModel(ok, minLen, historySize, rules);
  }

  private UserEntity resolveUser(String username) {
    if (username == null || username.isBlank()) return null;
    return userRepo.findByUserNameIgnoreCase(username).orElse(null);
  }

  private Set<String> enabledCodes() {
    Map<String, Boolean> configured = props.getPassword().getRules();
    int historySize = props.getPassword().getHistorySize();

    var enabled = new LinkedHashSet<String>();
    enabled.add(PasswordRuleCode.PASSWORD_TOO_SHORT.name());

    for (PasswordRule rule : availableRules) {
      PasswordRuleCode code = codeForRuleKey(rule.key());
      if (code == null) continue;

      boolean on = configured.containsKey(rule.key())
        ? Boolean.TRUE.equals(configured.get(rule.key()))
        : rule.enabledByDefault();

      if (on) enabled.add(code.name());
    }

    // sintéticas
    if (isEnabled(configured, "match", true)) enabled.add(PasswordRuleCode.PASSWORD_NOT_MATCH.name());
    if (isEnabled(configured, "sameAsCurrent", true)) enabled.add(PasswordRuleCode.PASSWORD_SAME_AS_CURRENT.name());
    if (historySize > 0 && isEnabled(configured, "history", true)) enabled.add(PasswordRuleCode.PASSWORD_REUSED_FROM_HISTORY.name());

    return enabled;
  }

  private boolean isEnabled(Map<String, Boolean> cfg, String key, boolean def) {
    if (cfg == null) return def;
    if (!cfg.containsKey(key)) return def;
    return Boolean.TRUE.equals(cfg.get(key));
  }

  private PasswordRuleCode codeForRuleKey(String key) {
    return switch (key) {
      case "lower" -> PasswordRuleCode.PASSWORD_MISSING_LOWER;
      case "upper" -> PasswordRuleCode.PASSWORD_MISSING_UPPER;
      case "digit" -> PasswordRuleCode.PASSWORD_MISSING_DIGIT;
      case "symbol" -> PasswordRuleCode.PASSWORD_MISSING_SPECIAL;
      case "noWhitespace" -> PasswordRuleCode.PASSWORD_HAS_WHITESPACE;
      case "noSequence" -> PasswordRuleCode.PASSWORD_HAS_SEQUENCE;
      case "noRepetition" -> PasswordRuleCode.PASSWORD_HAS_REPETITION;
      case "common" -> PasswordRuleCode.PASSWORD_COMMON;
      case "noUsername" -> PasswordRuleCode.PASSWORD_CONTAINS_USERNAME;
      default -> null;
    };
  }

  private PasswordRuleViews.Labels labels() {
    var locale = LocaleContextHolder.getLocale();

    return new PasswordRuleViews.Labels() {
      @Override public String minLen(int minLen) {
        return messageSource.getMessage(
          "password.rule.minLength", new Object[]{minLen}, "Mínimo de " + minLen + " caracteres", locale); }
      @Override public String lower() {
        return messageSource.getMessage(
          "password.rule.lower", null, "Ao menos 1 letra minúscula", locale); }
      @Override public String upper() {
        return messageSource.getMessage(
          "password.rule.upper", null, "Ao menos 1 letra maiúscula", locale); }
      @Override public String digit() {
        return messageSource.getMessage(
          "password.rule.digit", null, "Ao menos 1 número", locale); }
      @Override public String special() {
        return messageSource.getMessage(
          "password.rule.symbol", null, "Ao menos 1 caractere especial", locale); }
      @Override public String noWhitespace() {
        return messageSource.getMessage(
          "password.rule.noWhitespace", null, "Não conter espaços", locale); }
      @Override public String noSequence() {
        return messageSource.getMessage(
          "password.rule.noSequence", null, "Não sequências (123, abc)", locale); }
      @Override public String noRepetition() {
        return messageSource.getMessage(
          "password.rule.noRepetition", null, "Não conter repetição (aaa)", locale); }
      @Override public String common() {
        return messageSource.getMessage(
          "password.rule.common", null, "Não ser uma senha comum", locale); }
      @Override public String noUsername() {
        return messageSource.getMessage(
          "password.rule.noUsername", null, "Não conter usuário/e-mail", locale); }
      @Override public String match() {
        return messageSource.getMessage(
          "password.rule.match", null, "As senhas devem ser iguais", locale); }
      @Override public String history(int historySize) {
        return messageSource.getMessage(
          "password.rule.history",
          new Object[]{historySize},
          "Não reutilizar últimas " + historySize + " senhas",
          locale
        );
      }
      @Override public String sameAsCurrent() {
        return messageSource.getMessage(
          "password.rule.sameAsCurrent", null, "Nova senha diferente da atual", locale); }
    };
  }
}
