package com.cardsync.core.security.password;

import java.io.Serializable;

public record ExpiredPasswordFlow(String username, long issuedAtEpochMs) implements Serializable {

  public boolean isExpired(long nowEpochMs, long ttlMillis) {
    return (nowEpochMs - issuedAtEpochMs) > ttlMillis;
  }
}