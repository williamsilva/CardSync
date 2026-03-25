package com.cardsync.core.security.password;

import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordExpiryServiceImpl implements PasswordExpiryService {

  private final Clock clock;
  private final UserRepository users;
  private final CardsyncSecurityProperties props;

  @Override
  public boolean isExpiredPassword(String username) {
    return statusForUsername(username).expired();
  }

  @Override
  public PasswordExpiryViewModel statusForUsername(String username) {
    var passwordProps = props.getPassword();

    var userOpt = users.findByUserNameIgnoreCase(username);
    if (userOpt.isEmpty()) {
      // 🔐 não vaza existência
      return new PasswordExpiryViewModel(false, 0L, 0L);
    }

    var user = userOpt.get();

    int expireDays = passwordProps.getExpiration().getExpireDays();
    if (expireDays <= 0) {
      // expiração desabilitada por configuração
      return new PasswordExpiryViewModel(false, 0L, 0L);
    }

    Instant now = Instant.now(clock);

    // 1) Fonte da verdade: passwordExpiresAt
    OffsetDateTime passwordExpiresAt = user.getPasswordExpiresAt();
    if (passwordExpiresAt != null) {
      return buildVm(now, passwordExpiresAt.toInstant());
    }

    // 2) Fallback: passwordChangedAt + expireDays
    OffsetDateTime passwordChangedAt = user.getPasswordChangedAt();
    if (passwordChangedAt != null) {
      Instant expiresAt = passwordChangedAt.toInstant().plusSeconds(expireDaysToSeconds(expireDays));
      return buildVm(now, expiresAt);
    }

    // 3) Fallback final: createdAt + expireDays
    OffsetDateTime createdAt = user.getCreatedAt();
    if (createdAt != null) {
      Instant expiresAt = createdAt.toInstant().plusSeconds(expireDaysToSeconds(expireDays));
      return buildVm(now, expiresAt);
    }

    // 4) Nenhuma data disponível
    if (passwordProps.isFailIfNoExpirationData()) {
      log.warn("Password expiration fallback triggered (no expiration data found) for user={}", username);
      return new PasswordExpiryViewModel(true, 0L, 0L);
    }

    // modo DEV: não bloqueia
    return new PasswordExpiryViewModel(false, 0L, 0L);
  }

  private PasswordExpiryViewModel buildVm(Instant now, Instant expiresAt) {
    long expiresAtEpochMs = expiresAt.toEpochMilli();

    boolean expired = !expiresAt.isAfter(now);
    if (expired) {
      return new PasswordExpiryViewModel(true, expiresAtEpochMs, 0L);
    }

    long diffMs = expiresAtEpochMs - now.toEpochMilli();
    long dayMs = 86_400_000L;

    // ceil: se falta 1 hora, mostra 1 dia
    long daysRemaining = (diffMs + dayMs - 1) / dayMs;

    return new PasswordExpiryViewModel(false, expiresAtEpochMs, daysRemaining);
  }

  private long expireDaysToSeconds(int expireDays) {
    return (long) expireDays * 24L * 60L * 60L;
  }
}
