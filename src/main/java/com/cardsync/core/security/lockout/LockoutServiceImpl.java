package com.cardsync.core.security.lockout;

import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LockoutServiceImpl implements LockoutService {

  private final Clock clock;
  private final UserRepository userRepository;
  private final CardsyncSecurityProperties props;

  @Override
  @Transactional(readOnly = true)
  public LockoutState getLockoutState(String username) {
    var userOpt = userRepository.findByUserNameIgnoreCase(username);
    if (userOpt.isEmpty()) {
      return new LockoutState(false, 0L); // neutro
    }

    var u = userOpt.get();
    OffsetDateTime nowUtc = nowUtc();

    if (u.getBlockedUntil() == null || !u.getBlockedUntil().isAfter(nowUtc)) {
      return new LockoutState(false, 0L);
    }

    return new LockoutState(true, u.getBlockedUntil().toInstant().toEpochMilli());
  }

  @Override
  @Transactional
  public void registerFailure(String username) {
    onFailure(username);
  }

  @Override
  @Transactional
  public void registerSuccess(String username) {
    onSuccess(username);
  }

  @Override
  @Transactional
  public LockoutViewModel onFailure(String username) {
    var lockoutProps = props.getLockout();
    if (!lockoutProps.isEnabled()) {
      return LockoutViewModel.neutral();
    }

    var userOpt = userRepository.findByUserNameIgnoreCase(username);
    if (userOpt.isEmpty()) {
      // não vaza existência
      return LockoutViewModel.neutral();
    }

    var u = userOpt.get();
    OffsetDateTime now = nowUtc();

    // se o bloqueio expirou, limpa (mantém failedAttempts p/ progressivo)
    cleanupIfExpired(u, now);

    // se ainda está bloqueado
    if (isLocked(u, now)) {
      if (lockoutProps.isExtendWhenLocked()) {
        // estende baseado na regra aplicável ao failedAttempts atual
        Duration d = durationFor(u.getFailedAttempts());
        if (d != null && !d.isZero()) {
          u.setBlockedUntil(now.plus(d));
          userRepository.save(u);
        }
      }
      return toView(u, now);
    }

    // incrementa tentativas
    int attempts = Math.max(0, u.getFailedAttempts()) + 1;
    u.setFailedAttempts(attempts);

    // aplica bloqueio conforme regra (se houver)
    Duration blockFor = durationFor(attempts);
    if (blockFor != null && !blockFor.isZero()) {
      u.setBlockedUntil(now.plus(blockFor));
    }

    userRepository.save(u);
    return toView(u, now);
  }

  @Transactional
  public void onSuccess(String username) {
    var userOpt = userRepository.findByUserNameIgnoreCase(username);
    if (userOpt.isEmpty()) return;

    var u = userOpt.get();
    userRepository.markLoginSuccess(u.getId(), nowUtc());
  }

  @Override
  @Transactional
  public LockoutViewModel getState(String username) {
    var lockoutProps = props.getLockout();
    if (!lockoutProps.isEnabled()) {
      return LockoutViewModel.neutral();
    }

    var userOpt = userRepository.findByUserNameIgnoreCase(username);
    if (userOpt.isEmpty()) {
      return LockoutViewModel.neutral();
    }

    var u = userOpt.get();
    OffsetDateTime now = nowUtc();

    // se expirou, limpa persistindo (para não voltar locked no próximo submit)
    cleanupIfExpired(u, now);

    return toView(u, now);
  }

  // --------------------------
  // helpers
  // --------------------------

  private OffsetDateTime nowUtc() {
    return OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
  }

  private boolean isLocked(UserEntity u, OffsetDateTime nowUtc) {
    return u.getBlockedUntil() != null && u.getBlockedUntil().isAfter(nowUtc);
  }

  /**
   * Se bloqueio expirou, limpa blockedUntil e persiste.
   * NÃO zera failedAttempts para permitir progressivo (5->10->15->20).
   */
  private void cleanupIfExpired(UserEntity u, OffsetDateTime nowUtc) {
    if (u.getBlockedUntil() == null) return;

    if (u.getBlockedUntil().isAfter(nowUtc)) {
      return; // ainda bloqueado
    }

    u.setBlockedUntil(null);
    userRepository.save(u);
  }

  private LockoutViewModel toView(UserEntity u, OffsetDateTime nowUtc) {
    boolean locked = isLocked(u, nowUtc);

    long untilMs = 0L;
    if (locked && u.getBlockedUntil() != null) {
      untilMs = u.getBlockedUntil().toInstant().toEpochMilli();
    }

    int failed = Math.max(0, u.getFailedAttempts());

    // nextThreshold "bonito":
    // - se unlocked: próximo > failed
    // - se locked: próximo > threshold atual
    Integer next = locked
      ? nextThreshold(Math.max(failed, currentThreshold(failed)))
      : nextThreshold(failed);

    int remaining = 0;
    if (!locked && next != null) {
      remaining = Math.max(0, next - failed);
    }

    Duration nextDur = durationForThreshold(next);
    Long nextDurSeconds = (nextDur == null || nextDur.isZero()) ? null : nextDur.toSeconds();

    return new LockoutViewModel(locked, untilMs, failed, remaining, next, nextDurSeconds);
  }

  private int currentThreshold(int failedAttempts) {
    var lockoutProps = props.getLockout();
    int cur = 0;
    for (var r : lockoutProps.sortedRules()) {
      if (failedAttempts >= r.getAttempts()) cur = r.getAttempts();
      else break;
    }
    return cur;
  }

  /**
   * Duração do bloqueio aplicável ao número atual de tentativas (maior regra cujo attempts <= failedAttempts).
   */
  private Duration durationFor(int attempts) {
    var lockoutProps = props.getLockout();

    for (var r : lockoutProps.sortedRules()) {
      if (attempts == r.getAttempts()) {
        return r.getDuration() == null ? Duration.ZERO : r.getDuration();
      }
    }

    return Duration.ZERO;
  }

  /**
   * Duração exata da regra do threshold (para “Próximo bloqueio será de X”).
   */
  private Duration durationForThreshold(Integer threshold) {
    var lockoutProps = props.getLockout();
    if (threshold == null) return Duration.ZERO;

    for (var r : lockoutProps.sortedRules()) {
      if (r.getAttempts() == threshold) {
        return r.getDuration() == null ? Duration.ZERO : r.getDuration();
      }
    }
    return Duration.ZERO;
  }

  private Integer nextThreshold(int failedAttempts) {
    var lockoutProps = props.getLockout();
    for (var r : lockoutProps.sortedRules()) {
      if (r.getAttempts() > failedAttempts) {
        return r.getAttempts();
      }
    }
    return null;
  }
}
