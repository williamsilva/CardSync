package com.cardsync.core.security.lockout;

import java.io.Serializable;

public record LockoutViewModel(

  boolean locked,

  /**
   * Epoch millis (UTC).
   * 0 quando não estiver bloqueado.
   */
  long blockedUntilEpochMs,

  /**
   * Total de tentativas atuais.
   * 0 quando usuário não existir ou lockout desabilitado.
   */
  int failedAttempts,

  /**
   * Tentativas restantes até próximo threshold.
   * 0 quando não aplicável.
   */
  int remainingAttempts,

  /**
   * Próximo threshold configurado.
   * Null quando não houver.
   */
  Integer nextThreshold,
  Long nextLockDurationSeconds

) implements Serializable {

  public static LockoutViewModel neutral() {
    // usado quando usuário não existe (não vazar informação)
    return new LockoutViewModel(false, 0L, 0, 0, null, null);
  }
}
