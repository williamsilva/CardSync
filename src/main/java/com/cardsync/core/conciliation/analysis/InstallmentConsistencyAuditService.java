package com.cardsync.core.conciliation.analysis;

import com.cardsync.bff.controller.v1.representation.model.conciliation.InstallmentConsistencyAuditResult;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.repository.TransactionErpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Auditoria de consistência entre o total de parcelas declarado numa venda (installment) e a
 * quantidade real de linhas geradas em cs_installment_erp/cs_installment_acq.
 *
 * Achado real (2026-09-13): ErpAcquirerResolutionService#copyAcquirerToErp atualizava o total
 * declarado sem regenerar as parcelas - já corrigido, mas nada detectava esse tipo de
 * divergência sozinho. Esta auditoria é só leitura/alerta; não corrige nada automaticamente,
 * porque decidir COMO corrigir uma venda específica (regenerar por rateio, copiar da ACQ,
 * ignorar) é uma decisão caso a caso, não uma regra segura de aplicar em lote.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstallmentConsistencyAuditService {

  private static final int SAMPLE_SIZE = 50;

  private final TransactionErpRepository transactionErpRepository;
  private final TransactionAcqRepository transactionAcqRepository;

  @Transactional(readOnly = true)
  public InstallmentConsistencyAuditResult audit() {
    long erpMismatchCount = transactionErpRepository.countWithInstallmentCountMismatch();
    long acquirerMismatchCount = transactionAcqRepository.countWithInstallmentCountMismatch();

    List<UUID> erpSampleIds = erpMismatchCount == 0
      ? List.of()
      : transactionErpRepository.findIdsWithInstallmentCountMismatch(PageRequest.of(0, SAMPLE_SIZE));
    List<UUID> acquirerSampleIds = acquirerMismatchCount == 0
      ? List.of()
      : transactionAcqRepository.findIdsWithInstallmentCountMismatch(PageRequest.of(0, SAMPLE_SIZE));

    if (erpMismatchCount > 0 || acquirerMismatchCount > 0) {
      log.warn(
        "⚠ Auditoria de parcelas: {} venda(s) ERP e {} venda(s) ADQ com total declarado (installment) "
          + "diferente da quantidade de parcelas realmente geradas. amostraErp={}, amostraAcq={}",
        erpMismatchCount, acquirerMismatchCount, erpSampleIds, acquirerSampleIds
      );
    } else {
      log.info("✅ Auditoria de parcelas: nenhuma divergência entre total declarado e parcelas geradas (ERP e ADQ).");
    }

    return new InstallmentConsistencyAuditResult(erpMismatchCount, acquirerMismatchCount, erpSampleIds, acquirerSampleIds);
  }
}
