package com.cardsync.core.file.util;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class FileHashService {
  private static final int BUFFER_SIZE = 8192;

  /**
   * Calcula o SHA-256 sobre os bytes exatos do arquivo.
   * O nome e o caminho não participam da assinatura.
   */
  public String sha256(Path file) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[BUFFER_SIZE];

      try (InputStream input = Files.newInputStream(file)) {
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
          digest.update(buffer, 0, bytesRead);
        }
      }

      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException ex) {
      throw new IllegalStateException("Não foi possível calcular o hash do arquivo " + file.getFileName(), ex);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Algoritmo SHA-256 não disponível.", ex);
    }
  }
}
