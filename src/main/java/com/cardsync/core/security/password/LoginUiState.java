package com.cardsync.core.security.password;

import java.io.Serializable;

public record LoginUiState(
  boolean hasError,
  boolean locked,
  boolean expiredPassword,
  Long blockedUntilEpochMs,
  Integer failedAttempts,
  Integer remainingAttempts,
  Integer nextThreshold,
  Long nextLockDurationSeconds
) implements Serializable {
  public static final String SESSION_KEY = "CS_LOGIN_UI_STATE";
  public static final String USERNAME_KEY = "CS_LOGIN_USERNAME";

  public static LoginUiState clear() {
    return new LoginUiState(
      false, false, false, 0L, 0, 0, null, null);
  }
}
