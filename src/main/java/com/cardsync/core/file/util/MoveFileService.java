package com.cardsync.core.file.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

@Slf4j
@Service
public class MoveFileService {
  public void moveAfterCommit(Path file, String destination) {
    moveAfterCommit(file, destination, null);
  }

  public void moveAfterCommit(Path file, String destination, LocalDate fileDate) {
    register(file, destination, fileDate, null, null, false);
  }

  public void moveAfterCommitBank(Path file, String destination, LocalDate fileDate, Integer agency, Integer account) {
    register(file, destination, fileDate, agency, account, false);
  }

  public void moveAfterRollback(Path file, String destination) {
    moveAfterRollback(file, destination, null);
  }

  public void moveAfterRollback(Path file, String destination, LocalDate fileDate) {
    register(file, destination, fileDate, null, null, true);
  }

  public void moveAfterRollbackBank(Path file, String destination, LocalDate fileDate, Integer agency, Integer account) {
    register(file, destination, fileDate, agency, account, true);
  }

  public void moveNow(Path file, String destination) {
    moveNow(file, destination, null);
  }

  public void moveNow(Path file, String destination, LocalDate fileDate) {
    move(file, resolveArchiveDestination(destination, fileDate, null, null));
  }

  public void moveNowBank(Path file, String destination, LocalDate fileDate, Integer agency, Integer account) {
    move(file, resolveArchiveDestination(destination, fileDate, agency, account));
  }

  private void register(
    Path file,
    String destination,
    LocalDate fileDate,
    Integer agency,
    Integer account,
    boolean rollback
  ) {
    String resolvedDestination = resolveArchiveDestination(destination, fileDate, agency, account);

    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      move(file, resolvedDestination);
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (!rollback && status == STATUS_COMMITTED) move(file, resolvedDestination);
        if (rollback && status == STATUS_ROLLED_BACK) move(file, resolvedDestination);
      }
    });
  }

  private String resolveArchiveDestination(
    String destination,
    LocalDate fileDate,
    Integer agency,
    Integer account
  ) {
    if (destination == null || destination.isBlank()) {
      throw new IllegalArgumentException("Destino do arquivo não informado.");
    }

    int year = fileDate != null ? fileDate.getYear() : LocalDate.now().getYear();
    Path resolved = Paths.get(destination).resolve(String.valueOf(year));

    if (agency != null || account != null) {
      resolved = resolved.resolve(
        "agencia-" + normalizeBankPart(agency)
          + "_conta-" + normalizeBankPart(account)
      );
    }

    return resolved.toString();
  }

  private String normalizeBankPart(Integer value) {
    return value == null ? "nao-identificada" : String.valueOf(value);
  }

  private void move(Path file, String destination) {
    try {
      if (file == null) {
        log.warn("⚠ Movimento de arquivo ignorado: path nulo. destino={}", destination);
        return;
      }

      if (!Files.exists(file)) {
        log.warn("⚠ Movimento de arquivo ignorado: arquivo {} não existe mais. destino={}", file, destination);
        return;
      }

      Path destinationDir = Paths.get(destination);
      Files.createDirectories(destinationDir);

      Path target = resolveTarget(destinationDir, file.getFileName().toString());
      Files.move(file, target, StandardCopyOption.ATOMIC_MOVE);
      log.info("📂 Arquivo {} movido para {}", file.getFileName(), target);
    } catch (java.nio.file.AtomicMoveNotSupportedException atomicEx) {
      moveNonAtomic(file, destination);
    } catch (IOException ex) {
      throw new IllegalStateException("Erro ao mover arquivo " + safeName(file) + " para " + destination, ex);
    }
  }

  private void moveNonAtomic(Path file, String destination) {
    try {
      Path destinationDir = Paths.get(destination);
      Files.createDirectories(destinationDir);
      Path target = resolveTarget(destinationDir, file.getFileName().toString());
      Files.move(file, target);
      log.info("📂 Arquivo {} movido para {}", file.getFileName(), target);
    } catch (IOException ex) {
      throw new IllegalStateException("Erro ao mover arquivo " + safeName(file) + " para " + destination, ex);
    }
  }

  private Path resolveTarget(Path destinationDir, String originalName) {
    Path target = destinationDir.resolve(originalName);
    if (!Files.exists(target)) {
      return target;
    }

    String baseName = originalName;
    String extension = "";
    int dot = originalName.lastIndexOf('.');
    if (dot > 0) {
      baseName = originalName.substring(0, dot);
      extension = originalName.substring(dot);
    }

    int counter = 1;
    Path candidate;
    do {
      candidate = destinationDir.resolve(baseName + " (" + counter + ")" + extension);
      counter++;
    } while (Files.exists(candidate));

    log.warn("⚠ Já existe '{}' em {}; salvando como '{}'.", originalName, destinationDir, candidate.getFileName());
    return candidate;
  }

  private String safeName(Path file) {
    return file == null || file.getFileName() == null ? "<desconhecido>" : file.getFileName().toString();
  }
}
