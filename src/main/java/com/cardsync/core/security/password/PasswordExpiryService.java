package com.cardsync.core.security.password;

public interface PasswordExpiryService {

  boolean isExpiredPassword(String username);
  PasswordExpiryViewModel statusForUsername(String username);
}
