package com.cardsync.core.security.password.rules;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class NoUsernameRule implements PasswordRule {
  @Override public String key() { return "noUsername"; }
  @Override public String messageKey() { return "password.rule.noUsername"; }

  @Override
  public boolean matches(String rawPassword, PasswordRuleContext ctx) {
    if (rawPassword == null) return false;
    String userName = ctx != null ? ctx.username() : null;
    if (userName == null || userName.isBlank()) {
      // sem username: não penaliza o usuário (regra fica neutra)
      return true;
    }
    return !containsUserName(rawPassword, userName);
  }

  @Override
  public boolean matches(String rawPassword) {
    // sem contexto, não bloqueia
    return true;
  }

  private static boolean containsUserName(String password, String userName) {
    if (password == null || userName == null || userName.isBlank()) return false;

    String p = normalize(password);
    int at = userName.indexOf('@');
    String local = at > 0 ? userName.substring(0, at) : userName;

    return containsIdentityPart(p, local) || containsIdentityPart(p, userName);
  }

  private static boolean containsIdentityPart(String normalizedPassword, String identityRaw) {
    if (identityRaw == null || identityRaw.isBlank()) return false;

    String raw = Normalizer.normalize(identityRaw, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
    String normalized = normalize(raw).replaceAll("[^a-z0-9]", "");

    List<String> tokens = new ArrayList<>();
    if (normalized.length() >= 4) tokens.add(normalized);

    for (String t : raw.split("[\\s._-]+")) {
      String tt = normalize(t).replaceAll("[^a-z0-9]", "");
      if (tt.length() >= 4) tokens.add(tt);
    }

    return tokens.stream().anyMatch(normalizedPassword::contains);
  }

  private static String normalize(String s) {
    if (s == null) return "";
    String n = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
    return n.toLowerCase(Locale.ROOT).trim();
  }
}
