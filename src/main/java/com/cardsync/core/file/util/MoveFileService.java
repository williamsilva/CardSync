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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class MoveFileService {
  public void moveAfterCommit(Path file, String destination) { register(file, destination, false); }
  public void moveAfterRollback(Path file, String destination) { register(file, destination, true); }
  public void moveNow(Path file, String destination) { move(file, destination); }

  private void register(Path file, String destination, boolean rollback) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      move(file, destination);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCompletion(int status) {
        if (!rollback && status == STATUS_COMMITTED) move(file, destination);
        if (rollback && status == STATUS_ROLLED_BACK) move(file, destination);
      }
    });
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

      String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "_" + file.getFileName();
      Path target = Paths.get(destination, name);
      Files.createDirectories(target.getParent());
      Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
      log.info("📂 Arquivo {} movido para {}", file.getFileName(), target);
    } catch (IOException ex) {
      throw new IllegalStateException("Erro ao mover arquivo " + file.getFileName() + " para " + destination, ex);
    }
  }
}
