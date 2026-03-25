package com.cardsync.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class TokenHash {
  private TokenHash() {}

  public static String sha256Hex(String raw) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 não disponível", e);
    }
  }
}
