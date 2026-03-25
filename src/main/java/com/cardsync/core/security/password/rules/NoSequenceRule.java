package com.cardsync.core.security.password.rules;

import com.cardsync.core.security.CardsyncSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class NoSequenceRule implements PasswordRule {
  private final CardsyncSecurityProperties props;

  @Override public String key() { return "noSequence"; }
  @Override public String messageKey() { return "password.rule.noSequence"; }

  @Override
  public boolean matches(String rawPassword) {
    if (rawPassword == null) return false;
    int len = props.getPassword().getSequenceLen();
    if (len <= 1) return true;
    return !hasSequentialRun(rawPassword, len);
  }

  private static boolean hasSequentialRun(String raw, int sequenceLen) {
    if (raw == null || raw.length() < sequenceLen) return false;

    String s = raw.toLowerCase(Locale.ROOT);

    for (int i = 0; i <= s.length() - sequenceLen; i++) {
      boolean asc = true;
      boolean desc = true;

      for (int j = 1; j < sequenceLen; j++) {
        char prev = s.charAt(i + j - 1);
        char curr = s.charAt(i + j);

        boolean bothDigits = Character.isDigit(prev) && Character.isDigit(curr);
        boolean bothLetters = Character.isLetter(prev) && Character.isLetter(curr);
        if (!(bothDigits || bothLetters)) {
          asc = false;
          desc = false;
          break;
        }

        if (curr != prev + 1) asc = false;
        if (curr != prev - 1) desc = false;

        if (!asc && !desc) break;
      }

      if (asc || desc) return true;
    }

    return false;
  }
}
