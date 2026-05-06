package com.cardsync.core.file.scheduler;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.service.FileStorageTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileProcessingScheduler {

  private static final String TRIGGER_ERP = "SCHEDULER_ERP";
  private static final String TRIGGER_REDE = "SCHEDULER_REDE";
  private static final String TRIGGER_BANK = "SCHEDULER_BANK";

  private final FileProcessingProperties properties;
  private final FileStorageTask fileStorageTask;

  @Scheduled(cron = "${file-processing.scheduler.erp-cron:0 0/5 * * * *}", zone = "${cardsync.app.business-zone:America/Sao_Paulo}")
  public void processErp() {
    if (!isSchedulerEnabled() || !properties.getScheduler().isErpEnabled()) {
      logIdle("ERP");
      return;
    }
    fileStorageTask.tryProcessFileErp(TRIGGER_ERP);
  }

  @Scheduled(cron = "${file-processing.scheduler.rede-cron:0 0/10 * * * *}", zone = "${cardsync.app.business-zone:America/Sao_Paulo}")
  public void processRede() {
    if (!isSchedulerEnabled() || !properties.getScheduler().isRedeEnabled()) {
      logIdle("Rede");
      return;
    }
    fileStorageTask.tryProcessFileRede(TRIGGER_REDE);
  }

  @Scheduled(cron = "${file-processing.scheduler.bank-cron:0 0/15 * * * *}", zone = "${cardsync.app.business-zone:America/Sao_Paulo}")
  public void processBank() {
    if (!isSchedulerEnabled() || !properties.getScheduler().isBankEnabled()) {
      logIdle("Bank");
      return;
    }
    fileStorageTask.tryProcessFileBank(TRIGGER_BANK);
  }

  private boolean isSchedulerEnabled() {
    return properties.getScheduler() != null && properties.getScheduler().isEnabled();
  }

  private void logIdle(String system) {
    if (properties.getScheduler() != null && properties.getScheduler().isLogIdleCycles()) {
      log.debug("Agendamento de processamento {} desabilitado para este ciclo.", system);
    }
  }
}
