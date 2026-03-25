package com.cardsync.core.security.password.rules;

import com.cardsync.core.security.CardsyncSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CommonPasswordRule implements PasswordRule {

  private final CardsyncSecurityProperties props;

  @Override public String key() { return "common"; }
  @Override public String messageKey() { return "password.rule.common"; }

  @Override
  public boolean matches(String rawPassword) {
    if (rawPassword == null) return false;
    String normalized = normalize(rawPassword);

    Set<String> common = new HashSet<>();
    var list = props.getPassword().getCommonPasswords();
    if (list != null) {
      for (String s : list) {
        if (s != null && !s.isBlank()) common.add(normalize(s));
      }
    }
    // fallback seguro
    if (common.isEmpty()) {
      common.addAll(Set.of(
        "123456", "123456789", "qwerty", "password", "12345678", "111111",
        "123123", "abc123", "senha123", "admin123", "welcome", "letmein"
      ));
    }
    return !common.contains(normalized);
  }

  private static String normalize(String s) {
    if (s == null) return "";
    String n = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
    return n.toLowerCase(Locale.ROOT).trim();
  }
}
