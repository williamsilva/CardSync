package com.cardsync.core.security.password;

public record PasswordExpiryViewModel(
  boolean expired,
  long expiresAtEpochMs,
  long daysRemaining
) {
  public static PasswordExpiryViewModel notExpired(long expiresAtEpochMs, long daysRemaining) {
    return new PasswordExpiryViewModel(false, expiresAtEpochMs, daysRemaining);
  }

  public static PasswordExpiryViewModel expired(long expiresAtEpochMs) {
    return new PasswordExpiryViewModel(true, expiresAtEpochMs, 0);
  }
}
