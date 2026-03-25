package com.cardsync.core.security.password;

import com.cardsync.core.security.CardsyncSecurityProperties;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.PasswordHistory;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.repository.PasswordHistoryRepository;
import com.cardsync.domain.repository.ResetTokenRepository;
import com.cardsync.domain.service.PasswordTokenService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordService {

  private final Clock clock;
  private final PasswordEncoder encoder;
  private final CardsyncSecurityProperties props;
  private final PasswordPolicyService policyService;
  private final PasswordHistoryRepository historyRepo;
  private final PasswordTokenService passwordTokenService;
  private final ResetTokenRepository resetTokenRepository;

  /**
   * Valida a política COMPLETA (server-driven):
   * - regras configuradas via cardsync.security.password.rules
   * - min-length do servidor
   * - não permitir senha igual à atual
   * - não reutilizar as últimas N senhas (history-size)
   */
  public void assertValidPolicy(UserEntity user, String newRawPassword) {
    assertValidPolicy(user, newRawPassword, newRawPassword);
  }

  public void assertValidPolicy(UserEntity user, String newRawPassword, String confirmPassword) {
    String username = user != null ? user.getUserName() : null;
    var check = policyService.check(newRawPassword, confirmPassword, username);

    if (!check.ok()) {
      throw new IllegalArgumentException("Senha não atende a política");
    }
  }

  @Transactional
  public void changePassword(UserEntity user, String newRawPassword) {
    assertValidPolicy(user, newRawPassword);
    applyPasswordChange(user, newRawPassword);
  }

  @Transactional
  public void changePassword(UserEntity user, String newRawPassword, String confirmPassword) {
    assertValidPolicy(user, newRawPassword, confirmPassword);
    applyPasswordChange(user, newRawPassword);
  }

  private void applyPasswordChange(UserEntity user, String newRawPassword) {
    OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);

    // salva histórico da senha atual
    PasswordHistory ph = new PasswordHistory();
    ph.setUserId(user.getId());
    ph.setPasswordHash(user.getPasswordHash());
    ph.setCreatedAt(now);
    historyRepo.save(ph);

    // troca senha
    user.setPasswordHash(encoder.encode(newRawPassword));
    user.setPasswordChangedAt(now);

    if (props.getPassword().getExpiration().isEnabled()) {
      user.setPasswordExpiresAt(now.plusDays(props.getPassword().getExpiration().getExpireDays()));
    } else {
      user.setPasswordExpiresAt(null);
    }

    user.setFailedAttempts(0);
    user.setBlockedUntil(null);

    // regra central:
    // ao salvar/trocar senha, invalida todos os links pendentes
    // de primeira senha e reset para esse usuário
    passwordTokenService.invalidateAllPasswordTokens(user.getId());
  }

  private void assertResetRateLimit(UserEntity user) {
    var rateLimit = props.getPassword().getTokens().getRateLimit();

    if (rateLimit == null || !rateLimit.isEnabled()) {
      return;
    }

    OffsetDateTime threshold = nowUtc().minus(rateLimit.getWindow());

    long count = resetTokenRepository.countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), threshold);

    if (count >= rateLimit.getMaxRequests()) {
      throw BusinessException.notFound(
        ErrorCode.PASSWORD_RESET_RATE_LIMIT_EXCEEDED,
        "Too many password reset requests. Please try again later."
      );
    }
  }

  private OffsetDateTime nowUtc() {
    return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
  }
}