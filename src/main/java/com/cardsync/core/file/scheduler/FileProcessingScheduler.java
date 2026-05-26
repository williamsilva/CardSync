package com.cardsync.core.file.scheduler;

import com.cardsync.core.conciliation.analysis.ConciliationAnalysisService;
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
  private static final String TRIGGER_ERP_ACQUIRER_RECONCILIATION = "SCHEDULER_ERP_ACQUIRER_RECONCILIATION";
  private static final String TRIGGER_ERP_ACQUIRER_FEE_RECONCILIATION = "SCHEDULER_ERP_ACQUIRER_FEE_RECONCILIATION";

  private final FileProcessingProperties properties;
  private final FileStorageTask fileStorageTask;
  private final ConciliationAnalysisService conciliationAnalysisService;

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

  @Scheduled(cron = "${file-processing.scheduler.erp-acquirer-reconciliation-cron:0 0/15 * * * *}", zone = "${cardsync.app.business-zone:America/Sao_Paulo}")
  public void reconcileErpAcquirerSales() {
    if (!isSchedulerEnabled() || !properties.getScheduler().isErpAcquirerReconciliationEnabled()) {
      logIdle("Conciliação ERP x adquirente");
      return;
    }

    var result = conciliationAnalysisService.reconcileErpWithAcquirerBusinessContext(TRIGGER_ERP_ACQUIRER_RECONCILIATION);
    log.info(
      "✅ Conciliação ERP x adquirente finalizada: trigger={}, analisadas={}, conciliadas={}, atualizadas={}, bandeirasAtualizadas={}, contextoAtualizado={}, naoEncontradas={}, divergenciaValor={}, divergenciaAdquirente={}, ambiguas={}",
      TRIGGER_ERP_ACQUIRER_RECONCILIATION,
      result.analyzed(),
      result.matched(),
      result.updated(),
      result.flagUpdated(),
      result.businessContextUpdated(),
      result.notMatched(),
      result.valueDivergences(),
      result.acquirerDivergences(),
      result.ambiguousMatches()
    );
  }


  @Scheduled(cron = "${file-processing.scheduler.erp-acquirer-fee-reconciliation-cron:0 10/15 * * * *}", zone = "${cardsync.app.business-zone:America/Sao_Paulo}")
  public void reconcileErpAcquirerFees() {
    if (!isSchedulerEnabled() || !properties.getScheduler().isErpAcquirerFeeReconciliationEnabled()) {
      logIdle("Conciliação de taxas ERP x adquirente");
      return;
    }

    var result = conciliationAnalysisService.reconcileErpAcquirerFees(TRIGGER_ERP_ACQUIRER_FEE_RECONCILIATION);
    log.info(
      "✅ Conciliação de taxas ERP x adquirente finalizada: trigger={}, analisadas={}, erpAtualizadas={}, divergenciasTaxa={}, semContratoValido={}, taxasOk={}, semAdquirente={}",
      TRIGGER_ERP_ACQUIRER_FEE_RECONCILIATION,
      result.analyzed(),
      result.updatedErpSales(),
      result.divergentRates(),
      result.missingValidContracts(),
      result.okRates(),
      result.skippedWithoutAcquirer()
    );
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
