package com.cardsync.core.file.service;

import com.cardsync.core.file.runtime.FileProcessingExecutionStatus;
import com.cardsync.core.file.runtime.FileProcessingSystemType;
import com.cardsync.domain.model.FileProcessingExecutionStateEntity;
import com.cardsync.domain.model.enums.FileProcessingTriggerType;
import com.cardsync.infrastructure.repository.FileProcessingExecutionStateRepository;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageTask {

  private static final String NO_EXECUTION_MESSAGE = "Nenhuma execução registrada.";
  private static final String RUNNING_MESSAGE = "Processamento em execução.";

  private final ProcessFileErpService processFileErpService;
  private final ProcessFileAcquirerService processFileAcquirerService;
  private final ProcessFileBankService processFileBankService;
  private final FileProcessingExecutionStateRepository executionStateRepository;

  private final AtomicBoolean erpRunning = new AtomicBoolean(false);
  private final AtomicBoolean acquirerRunning = new AtomicBoolean(false);
  private final AtomicBoolean bankRunning = new AtomicBoolean(false);

  private final AtomicReference<ExecutionState> erpState = new AtomicReference<>(ExecutionState.initial(FileProcessingSystemType.ERP));
  private final AtomicReference<ExecutionState> acquirerState = new AtomicReference<>(ExecutionState.initial(FileProcessingSystemType.ACQUIRER));
  private final AtomicReference<ExecutionState> bankState = new AtomicReference<>(ExecutionState.initial(FileProcessingSystemType.BANK));

  @PostConstruct
  private void restoreStateFromDb() {
    Arrays.stream(FileProcessingSystemType.values()).forEach(system -> {
      executionStateRepository.findBySystem(system.getCode()).ifPresent(entity -> {
        AtomicReference<ExecutionState> stateRef = stateRefFor(system);
        FileProcessingTriggerType trigger = entity.getLastTrigger() != null
          ? Arrays.stream(FileProcessingTriggerType.values())
              .filter(t -> t.getCode().equals(entity.getLastTrigger()))
              .findFirst().orElse(null)
          : null;
        stateRef.set(ExecutionState.builder()
          .system(system)
          .lastStartedAt(entity.getLastStartedAt())
          .lastFinishedAt(entity.getLastFinishedAt())
          .lastSuccess(entity.getLastSuccess())
          .lastTrigger(trigger)
          .lastMessage(entity.getLastMessage())
          .build());
      });
    });
  }

  public void processFileErp() {
    if (!tryProcessFileErp(FileProcessingTriggerType.MANUAL)) {
      throw new IllegalStateException("Processamento ERP já está em execução.");
    }
  }

  public void processFileAcquirer() {
    if (!tryProcessFileAcquirer(FileProcessingTriggerType.MANUAL)) {
      throw new IllegalStateException("Processamento de adquirentes já está em execução.");
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

  public boolean tryProcessFileAcquirer(FileProcessingTriggerType trigger) {
    return execute(FileProcessingSystemType.ACQUIRER, trigger, acquirerRunning, acquirerState, processFileAcquirerService::processFiles);
  }

  public boolean tryProcessFileBank(FileProcessingTriggerType trigger) {
    return execute(FileProcessingSystemType.BANK, trigger, bankRunning, bankState, processFileBankService::processFiles);
  }

  public FileProcessingExecutionStatus erpStatus() {
    return erpState.get().toStatus(erpRunning.get());
  }

  public FileProcessingExecutionStatus acquirerStatus() {
    return acquirerState.get().toStatus(acquirerRunning.get());
  }

  public FileProcessingExecutionStatus bankStatus() {
    return bankState.get().toStatus(bankRunning.get());
  }

  public boolean isAnyManualRunning() {
    return isManualRunning(erpRunning, erpState)
      || isManualRunning(acquirerRunning, acquirerState)
      || isManualRunning(bankRunning, bankState);
  }

  private boolean isManualRunning(AtomicBoolean running, AtomicReference<ExecutionState> stateRef) {
    return running.get() && stateRef.get().getLastTrigger() == FileProcessingTriggerType.MANUAL;
  }

  private AtomicReference<ExecutionState> stateRefFor(FileProcessingSystemType system) {
    return switch (system) {
      case ERP      -> erpState;
      case ACQUIRER -> acquirerState;
      case BANK     -> bankState;
    };
  }

  private void persistState(FileProcessingSystemType system, ExecutionState state) {
    try {
      FileProcessingExecutionStateEntity entity = executionStateRepository
        .findBySystem(system.getCode())
        .orElseGet(FileProcessingExecutionStateEntity::new);
      entity.setSystem(system.getCode());
      entity.setLastStartedAt(state.getLastStartedAt());
      entity.setLastFinishedAt(state.getLastFinishedAt());
      entity.setLastSuccess(state.getLastSuccess());
      entity.setLastTrigger(state.getLastTrigger() != null ? state.getLastTrigger().getCode() : null);
      entity.setLastMessage(state.getLastMessage());
      entity.setUpdatedAt(OffsetDateTime.now());
      executionStateRepository.save(entity);
    } catch (Exception ex) {
      log.warn("⚠ Falha ao persistir estado de execução para {}. erro={}", system.getCode(), ex.getMessage());
    }
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
      ExecutionState successState = stateRef.get().finished(finishedAt, true, message);
      stateRef.set(successState);
      persistState(system, successState);
      log.info("✅ Processamento de arquivos {} concluído. trigger={}, duração={}s",
        system.getCode(), trigger.getCode(), Duration.between(startedAt, finishedAt).toSeconds());
      return true;
    } catch (Exception ex) {
      OffsetDateTime finishedAt = OffsetDateTime.now();
      String message = safeMessage(ex);
      ExecutionState failedState = stateRef.get().finished(finishedAt, false, message);
      stateRef.set(failedState);
      persistState(system, failedState);
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
