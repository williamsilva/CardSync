package com.cardsync.core.security.authserver;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

final class KeyUtils {
  private KeyUtils() {}

  static KeyPair generateRsaKey() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA not available", e);
    }
  }

  static String kid() {
    return UUID.randomUUID().toString();
  }
}
