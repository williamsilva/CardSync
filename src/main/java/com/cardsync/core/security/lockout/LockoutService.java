package com.cardsync.core.security.lockout;

public interface LockoutService {

  void registerFailure(String username);
  void registerSuccess(String username);
  LockoutState getLockoutState(String username);
  record LockoutState(boolean locked, long blockedUntilEpochMs) {}

  /** usado no handler de falha: incrementa e retorna estado atualizado */
  LockoutViewModel onFailure(String username);

  /** usado para consultar sem alterar */
  LockoutViewModel getState(String username);
}
