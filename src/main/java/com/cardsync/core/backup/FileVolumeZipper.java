package com.cardsync.core.backup;

import com.cardsync.core.file.config.FileProcessingProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Compacta toda a árvore de {@code file-processing.base-path} (Volume de arquivos — ERP, banco,
 * adquirente já vivem dentro dela, ver FileProcessingProperties) num único zip, sem depender de
 * binário externo (java.util.zip). Usado por BackupService.
 */
@Component
@RequiredArgsConstructor
public class FileVolumeZipper {

  private final FileProcessingProperties properties;

  public void zipInto(ZipOutputStream zipOut, String entryPrefix) {
    String basePath = properties.getBasePath();
    if (basePath == null || basePath.isBlank()) {
      throw new IllegalStateException("file-processing.base-path não configurado");
    }

    Path root = Paths.get(basePath);
    if (!Files.isDirectory(root)) {
      throw new IllegalStateException("Pasta de arquivos não encontrada: " + basePath);
    }

    try (Stream<Path> paths = Files.walk(root)) {
      paths.filter(Files::isRegularFile).forEach(file -> {
        try {
          String relative = root.relativize(file).toString().replace('\\', '/');
          zipOut.putNextEntry(new ZipEntry(entryPrefix + "/" + relative));
          Files.copy(file, zipOut);
          zipOut.closeEntry();
        } catch (IOException e) {
          throw new UncheckedIOException("Falha ao compactar " + file, e);
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException("Falha ao percorrer a pasta de arquivos: " + basePath, e);
    }
  }
}
