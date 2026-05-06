package com.cardsync.core.file.service;

import com.cardsync.core.file.runtime.FileProcessingExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageTask {
  private final ProcessFileErpService processFileErpService;
  private final ProcessFileRedeService processFileRedeService;
  private final ProcessFileBankService processFileBankService;

  private final AtomicBoolean erpRunning = new AtomicBoolean(false);
  private final AtomicBoolean redeRunning = new AtomicBoolean(false);
  private final AtomicBoolean bankRunning = new AtomicBoolean(false);

  private final AtomicReference<ExecutionState> erpState = new AtomicReference<>(ExecutionState.initial("ERP"));
  private final AtomicReference<ExecutionState> redeState = new AtomicReference<>(ExecutionState.initial("REDE"));
  private final AtomicReference<ExecutionState> bankState = new AtomicReference<>(ExecutionState.initial("BANK"));

  public void processFileErp() {
    if (!tryProcessFileErp("MANUAL")) {
      throw new IllegalStateException("Processamento ERP já está em execução.");
    }
  }

  public void processFileRede() {
    if (!tryProcessFileRede("MANUAL")) {
      throw new IllegalStateException("Processamento Rede já está em execução.");
    }
  }

  public void processFileBank() {
    if (!tryProcessFileBank("MANUAL")) {
      throw new IllegalStateException("Processamento bancário já está em execução.");
    }
  }

  public boolean tryProcessFileErp(String trigger) {
    return execute("ERP", trigger, erpRunning, erpState, processFileErpService::processFiles);
  }

  public boolean tryProcessFileRede(String trigger) {
    return execute("REDE", trigger, redeRunning, redeState, processFileRedeService::processFiles);
  }

  public boolean tryProcessFileBank(String trigger) {
    return execute("BANK", trigger, bankRunning, bankState, processFileBankService::processFiles);
  }

  public FileProcessingExecutionStatus erpStatus() {
    return erpState.get().toStatus(erpRunning.get());
  }

  public FileProcessingExecutionStatus redeStatus() {
    return redeState.get().toStatus(redeRunning.get());
  }

  public FileProcessingExecutionStatus bankStatus() {
    return bankState.get().toStatus(bankRunning.get());
  }

  private boolean execute(
    String system,
    String trigger,
    AtomicBoolean running,
    AtomicReference<ExecutionState> stateRef,
    Runnable processor
  ) {
    if (!running.compareAndSet(false, true)) {
      log.warn("⚠ Processamento {} ignorado. Já existe uma execução em andamento. trigger={}", system, trigger);
      return false;
    }

    OffsetDateTime startedAt = OffsetDateTime.now();
    stateRef.set(stateRef.get().started(trigger, startedAt));

    try {
      log.info("📌 Iniciando processamento de arquivos {}. trigger={}", system, trigger);
      processor.run();
      OffsetDateTime finishedAt = OffsetDateTime.now();
      String message = "Processamento concluído em " + Duration.between(startedAt, finishedAt).toSeconds() + "s";
      stateRef.set(stateRef.get().finished(finishedAt, true, message));
      log.info("✅ Processamento de arquivos {} concluído. trigger={}, duração={}s",
        system, trigger, Duration.between(startedAt, finishedAt).toSeconds());
      return true;
    } catch (Exception ex) {
      OffsetDateTime finishedAt = OffsetDateTime.now();
      String message = safeMessage(ex);
      stateRef.set(stateRef.get().finished(finishedAt, false, message));
      log.error("❌ Processamento de arquivos {} falhou. trigger={}, duração={}s, erro={}",
        system, trigger, Duration.between(startedAt, finishedAt).toSeconds(), message, ex);
      throw ex;
    } finally {
      running.set(false);
    }
  }

  private String safeMessage(Exception ex) {
    if (ex.getMessage() == null || ex.getMessage().isBlank()) {
      return ex.getClass().getSimpleName();
    }
    return ex.getMessage().length() > 500 ? ex.getMessage().substring(0, 500) : ex.getMessage();
  }

  private record ExecutionState(
    String system,
    OffsetDateTime lastStartedAt,
    OffsetDateTime lastFinishedAt,
    Boolean lastSuccess,
    String lastTrigger,
    String lastMessage
  ) {
    static ExecutionState initial(String system) {
      return new ExecutionState(system, null, null, null, null, "Nenhuma execução registrada.");
    }

    ExecutionState started(String trigger, OffsetDateTime startedAt) {
      return new ExecutionState(system, startedAt, lastFinishedAt, lastSuccess, trigger, "Processamento em execução.");
    }

    ExecutionState finished(OffsetDateTime finishedAt, boolean success, String message) {
      return new ExecutionState(system, lastStartedAt, finishedAt, success, lastTrigger, message);
    }

    FileProcessingExecutionStatus toStatus(boolean running) {
      return new FileProcessingExecutionStatus(system, running, lastStartedAt, lastFinishedAt, lastSuccess, lastTrigger, lastMessage);
    }
  }
}
