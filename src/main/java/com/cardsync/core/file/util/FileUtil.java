package com.cardsync.core.file.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileUtil {
  private FileUtil() {}

  public static boolean isTextFile(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      byte[] bytes = in.readNBytes(512);
      for (byte b : bytes) if (b == 0) return false;
      return true;
    } catch (IOException ex) {
      return false;
    }
  }
}
