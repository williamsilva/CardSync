package com.cardsync.core.file.scheduler;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.service.FileStorageTask;
import com.cardsync.core.reconciliation.pipeline.FinancialReconciliationPipelineResult;
import com.cardsync.core.reconciliation.pipeline.FinancialReconciliationPipelineService;
import com.cardsync.domain.model.enums.FileProcessingTriggerType;
import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSequencialService {

  private final AtomicBoolean running = new AtomicBoolean(false);

  private final FileStorageTask fileStorageTask;
  private final FileProcessingProperties properties;
  private final FinancialReconciliationPipelineService financialReconciliationPipelineService;

  @PostConstruct
  public void logSchedulerConfiguration() {
    if (properties.getScheduler() == null) {
      log.warn("⚠️ Esteira completa CardSync criada, mas file-processing.scheduler não foi carregado.");
      return;
    }

    log.info(
      "🕒 Esteira completa CardSync configurada: schedulerEnabled={}, completePipelineEnabled={}, cron={}, stopOnStepError={}",
      properties.getScheduler().isEnabled(),
      properties.getScheduler().isCompletePipelineEnabled(),
      properties.getScheduler().getCompletePipelineCron(),
      properties.getScheduler().isCompletePipelineStopOnStepError()
    );
  }

  @Scheduled(
    cron = "${file-processing.scheduler.complete-pipeline-cron:0 0/5 * * * *}",
    zone = "${cardsync.app.business-zone:America/Sao_Paulo}"
  )
  @SchedulerLock(
    name = "JobSequencialService_startSequence",
    lockAtMostFor = "${file-processing.scheduler.lock-at-most-for:PT30M}",
    lockAtLeastFor = "${file-processing.scheduler.lock-at-least-for:PT10S}"
  )
  public void startSequence() {
    if (!isSchedulerEnabled() || !properties.getScheduler().isCompletePipelineEnabled()) {
      logIdle();
      return;
    }

    if (!running.compareAndSet(false, true)) {
      log.warn("⏭️ Esteira completa ignorada: já existe uma execução em andamento.");
      return;
    }

    OffsetDateTime startedAt = OffsetDateTime.now();

    try {
      log.info("▶ ESTEIRA COMPLETA CARDSYNC iniciado às {}", startedAt);

      executeStep("1. Processamento de arquivos ERP", () -> runFileStep(
        "ERP",
        () -> fileStorageTask.tryProcessFileErp(FileProcessingTriggerType.SCHEDULER_SEQUENTIAL_ERP)
      ));

      executeStep("2. Processamento de arquivos REDE/adquirente", () -> runFileStep(
        "REDE/adquirente",
        () -> fileStorageTask.tryProcessFileRede(FileProcessingTriggerType.SCHEDULER_SEQUENTIAL_REDE)
      ));

      executeStep("3. Processamento de arquivos Banco/CNAB", () -> runFileStep(
        "Banco/CNAB",
        () -> fileStorageTask.tryProcessFileBank(FileProcessingTriggerType.SCHEDULER_SEQUENTIAL_BANK)
      ));

      executeStep("4. Esteira financeira completa", () -> financialReconciliationPipelineService.run(
        FinancialReconciliationTriggerType.SCHEDULER_SEQUENTIAL_JOB
      ));

      OffsetDateTime finishedAt = OffsetDateTime.now();
      log.info(
        "✅ ESTEIRA COMPLETA CARDSYNC finalizado às {}. duração={}s",
        finishedAt,
        Duration.between(startedAt, finishedAt).toSeconds()
      );
    } catch (Exception ex) {
      OffsetDateTime failedAt = OffsetDateTime.now();
      log.error(
        "❌ ESTEIRA COMPLETA CARDSYNC falhou às {}. duraçãoAtéFalha={}s, erro={}",
        failedAt,
        Duration.between(startedAt, failedAt).toSeconds(),
        safeMessage(ex),
        ex
      );
    } finally {
      running.set(false);
    }
  }

  private Boolean runFileStep(String name, Supplier<Boolean> action) {
    Boolean executed = action.get();

    if (Boolean.FALSE.equals(executed)) {
      // 'false' aqui significa que JÁ existe um processamento daquele sistema em andamento
      // (ex.: disparo manual concorrente). Isso NÃO é um erro da esteira: a etapa é apenas
      // pulada e a esteira segue para as próximas. O flag stopOnStepError governa erros reais
      // (exceções), não esse caso de concorrência.
      log.warn("⏭️ Etapa {} pulada: já existe um processamento desse sistema em andamento. A esteira continua.", name);
    }

    return executed;
  }

  private <T> T executeStep(String stepName, Supplier<T> action) {
    OffsetDateTime startedAt = OffsetDateTime.now();
    log.info("▶ Iniciando etapa da esteira completa: {} às {}", stepName, startedAt);

    try {
      T result = action.get();
      OffsetDateTime finishedAt = OffsetDateTime.now();
      log.info(
        "✅ Etapa da esteira completa finalizada: {} às {}. duração={}s",
        stepName,
        finishedAt,
        Duration.between(startedAt, finishedAt).toSeconds()
      );

      if (result instanceof FinancialReconciliationPipelineResult pipelineResult) {
        log.info(
          "📘 Resultado esteira financeira: trigger={}, etapas={}, startedAt={}, finishedAt={}",
          pipelineResult.getTrigger(),
          pipelineResult.getSteps() == null ? 0 : pipelineResult.getSteps().size(),
          pipelineResult.getStartedAt(),
          pipelineResult.getFinishedAt()
        );
      }

      return result;
    } catch (Exception ex) {
      OffsetDateTime failedAt = OffsetDateTime.now();
      log.error(
        "❌ Etapa da esteira completa falhou: {} às {}. duraçãoAtéFalha={}s, erro={}",
        stepName,
        failedAt,
        Duration.between(startedAt, failedAt).toSeconds(),
        safeMessage(ex),
        ex
      );

      if (properties.getScheduler().isCompletePipelineStopOnStepError()) {
        throw ex;
      }

      return null;
    }
  }

  private boolean isSchedulerEnabled() {
    return properties.getScheduler() != null && properties.getScheduler().isEnabled();
  }

  private void logIdle() {
    if (properties.getScheduler() != null && properties.getScheduler().isLogIdleCycles()) {
      log.debug("Esteira completa CardSync desabilitada para este ciclo.");
    }
  }

  private String safeMessage(Exception ex) {
    if (ex.getMessage() == null || ex.getMessage().isBlank()) {
      return ex.getClass().getSimpleName();
    }

    return ex.getMessage().length() > 500 ? ex.getMessage().substring(0, 500) : ex.getMessage();
  }
}