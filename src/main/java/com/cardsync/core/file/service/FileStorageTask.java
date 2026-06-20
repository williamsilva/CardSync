package com.cardsync.core.file.service;

import com.cardsync.core.file.runtime.FileProcessingExecutionStatus;
import com.cardsync.core.file.runtime.FileProcessingSystemType;
import com.cardsync.domain.model.enums.FileProcessingTriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

  private static final String NO_EXECUTION_MESSAGE = "Nenhuma execução registrada.";
  private static final String RUNNING_MESSAGE = "Processamento em execução.";

  private final ProcessFileErpService processFileErpService;
  private final ProcessFileRedeService processFileRedeService;
  private final ProcessFileBankService processFileBankService;

  private final AtomicBoolean erpRunning = new AtomicBoolean(false);
  private final AtomicBoolean redeRunning = new AtomicBoolean(false);
  private final AtomicBoolean bankRunning = new AtomicBoolean(false);

  private final AtomicReference<ExecutionState> erpState = new AtomicReference<>(ExecutionState.initial(FileProcessingSystemType.ERP));
  private final AtomicReference<ExecutionState> redeState = new AtomicReference<>(ExecutionState.initial(FileProcessingSystemType.REDE));
  private final AtomicReference<ExecutionState> bankState = new AtomicReference<>(ExecutionState.initial(FileProcessingSystemType.BANK));

  public void processFileErp() {
    if (!tryProcessFileErp(FileProcessingTriggerType.MANUAL)) {
      throw new IllegalStateException("Processamento ERP já está em execução.");
    }
  }

  public void processFileRede() {
    if (!tryProcessFileRede(FileProcessingTriggerType.MANUAL)) {
      throw new IllegalStateException("Processamento Rede já está em execução.");
    }
  }

  public void processFileBank() {
    if (!tryProcessFileBank(FileProcessingTriggerType.MANUAL)) {
      throw new IllegalStateException("Processamento bancário já está em execução.");
    }
  }

  public boolean tryProcessFileErp(FileProcessingTriggerType trigger) {
    return execute(FileProcessingSystemType.ERP, trigger, erpRunning, erpState, processFileErpService::processFiles);
  }

  public boolean tryProcessFileRede(FileProcessingTriggerType trigger) {
    return execute(FileProcessingSystemType.REDE, trigger, redeRunning, redeState, processFileRedeService::processFiles);
  }

  public boolean tryProcessFileBank(FileProcessingTriggerType trigger) {
    return execute(FileProcessingSystemType.BANK, trigger, bankRunning, bankState, processFileBankService::processFiles);
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

  public boolean isAnyManualRunning() {
    return isManualRunning(erpRunning, erpState)
      || isManualRunning(redeRunning, redeState)
      || isManualRunning(bankRunning, bankState);
  }

  private boolean isManualRunning(AtomicBoolean running, AtomicReference<ExecutionState> stateRef) {
    return running.get() && stateRef.get().getLastTrigger() == FileProcessingTriggerType.MANUAL;
  }

  private boolean execute(
    FileProcessingSystemType system,
    FileProcessingTriggerType trigger,
    AtomicBoolean running,
    AtomicReference<ExecutionState> stateRef,
    Runnable processor
  ) {
    if (!running.compareAndSet(false, true)) {
      log.warn("⚠ Processamento {} ignorado. Já existe uma execução em andamento. trigger={}", system.getCode(), trigger.getCode());
      return false;
    }

    OffsetDateTime startedAt = OffsetDateTime.now();
    stateRef.set(stateRef.get().started(trigger, startedAt));

    try {
      log.info("📌 Iniciando processamento de arquivos {}. trigger={}", system.getCode(), trigger.getCode());
      processor.run();
      OffsetDateTime finishedAt = OffsetDateTime.now();
      String message = "Processamento concluído em " + Duration.between(startedAt, finishedAt).toSeconds() + "s";
      stateRef.set(stateRef.get().finished(finishedAt, true, message));
      log.info("✅ Processamento de arquivos {} concluído. trigger={}, duração={}s",
        system.getCode(), trigger.getCode(), Duration.between(startedAt, finishedAt).toSeconds());
      return true;
    } catch (Exception ex) {
      OffsetDateTime finishedAt = OffsetDateTime.now();
      String message = safeMessage(ex);
      stateRef.set(stateRef.get().finished(finishedAt, false, message));
      log.error("❌ Processamento de arquivos {} falhou. trigger={}, duração={}s, erro={}",
        system.getCode(), trigger.getCode(), Duration.between(startedAt, finishedAt).toSeconds(), message, ex);
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

  @Getter
  @Builder(toBuilder = true)
  @NoArgsConstructor
  @AllArgsConstructor
  private static class ExecutionState {

    private FileProcessingSystemType system;
    private OffsetDateTime lastStartedAt;
    private OffsetDateTime lastFinishedAt;
    private Boolean lastSuccess;
    private FileProcessingTriggerType lastTrigger;
    private String lastMessage;

    private static ExecutionState initial(FileProcessingSystemType system) {
      return ExecutionState.builder()
        .system(system)
        .lastMessage(NO_EXECUTION_MESSAGE)
        .build();
    }

    private ExecutionState started(FileProcessingTriggerType trigger, OffsetDateTime startedAt) {
      return this.toBuilder()
        .lastStartedAt(startedAt)
        .lastTrigger(trigger)
        .lastMessage(RUNNING_MESSAGE)
        .build();
    }

    private ExecutionState finished(OffsetDateTime finishedAt, boolean success, String message) {
      return this.toBuilder()
        .lastFinishedAt(finishedAt)
        .lastSuccess(success)
        .lastMessage(message)
        .build();
    }

    private FileProcessingExecutionStatus toStatus(boolean running) {
      return FileProcessingExecutionStatus.builder()
        .system(system)
        .running(running)
        .lastStartedAt(lastStartedAt)
        .lastFinishedAt(lastFinishedAt)
        .lastSuccess(lastSuccess)
        .lastTrigger(lastTrigger)
        .lastMessage(lastMessage)
        .build();
    }
  }
}
