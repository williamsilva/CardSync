package com.cardsync.core.security.password;

import com.cardsync.core.security.CardsyncSecurityProperties;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LockoutPolicy {

  private final Clock clock;
  private final CardsyncSecurityProperties props;

  public OffsetDateTime computeBlockedUntil(int failedAttempts) {
    var thresholds = props.getLockout().getRules();
    int minutes = 0;

    for (var t : thresholds) {
      if (failedAttempts >= t.getAttempts()) {
        minutes = t.getDuration().toMinutesPart();
      }
    }

    if (minutes <= 0) return null;
    return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).plusMinutes(minutes);
  }
}
