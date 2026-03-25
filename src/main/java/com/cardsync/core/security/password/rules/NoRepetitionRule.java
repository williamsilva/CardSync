package com.cardsync.core.security.password.rules;

import com.cardsync.core.security.CardsyncSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NoRepetitionRule implements PasswordRule {
  private final CardsyncSecurityProperties props;

  @Override public String key() { return "noRepetition"; }
  @Override public String messageKey() { return "password.rule.noRepetition"; }

  @Override
  public boolean matches(String rawPassword) {
    if (rawPassword == null) return false;
    int max = props.getPassword().getMaxSameInRow();
    if (max <= 1) return true;
    return !hasRepetition(rawPassword, max);
  }

  private static boolean hasRepetition(String raw, int maxSameInRow) {
    if (raw == null || raw.isEmpty()) return false;

    int run = 1;
    for (int i = 1; i < raw.length(); i++) {
      if (raw.charAt(i) == raw.charAt(i - 1)) {
        run++;
        if (run >= maxSameInRow) return true;
      } else {
        run = 1;
      }
    }
    return false;
  }
}
