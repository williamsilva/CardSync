package com.cardsync.core.conciliation.analysis;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconcileErpAcquirerResultModel;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.model.enums.StatusTransactionReasonEnum;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.repository.TransactionErpRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Conciliação ERP x Adquirente para transações ERP MANUAIS com NSU e autorização
 * invertidos.

 * Em transações manuais, por vezes o NSU é digitado no campo de autorização e a
 * autorização no campo de NSU. A conciliação principal (que casa por NSU+autorização
 * na posição correta) não encontra essas vendas. Esta etapa roda DEPOIS da principal
 * e tenta casar apenas as vendas manuais ainda pendentes, usando a chave de identidade
 * com NSU e autorização TROCADOS. Todas as demais regras (valor, adquirente, score,
 * ambiguidade) e o efeito do match (copiar contexto comercial e marcar RECONCILED) são
 * exatamente os mesmos da conciliação principal — reaproveitados de
 * {@link ConciliationAnalysisService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConciliationManualSwapReconciliationService {

  private final EntityManager entityManager;
  private final TransactionErpRepository transactionErpRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final ConciliationAnalysisService conciliationAnalysisService;

  @Transactional
  public ReconcileErpAcquirerResultModel reconcileManualSwapped() {
    return reconcileManualSwapped("MANUAL");
  }

  @Transactional
  public ReconcileErpAcquirerResultModel reconcileManualSwapped(String trigger) {
    // Esta etapa nunca reprocessa vendas já conciliadas: ela só atua sobre o que
    // sobrou pendente após a conciliação principal.
    List<Integer> pendingStatuses = conciliationAnalysisService.erpAcquirerPendingStatusCodes();

    List<UUID> erpIds = transactionErpRepository.findIdsForManualSwapReconciliation(
      pendingStatuses,
      CaptureEnum.MANUAL.getCode(),
      List.of(
        StatusTransactionReasonEnum.CV_NOT_FOUND_ADQ.getCode(),
        StatusTransactionReasonEnum.VALUE_MISMATCH.getCode(),
        StatusTransactionReasonEnum.ACQUIRER_MISMATCH.getCode(),
        StatusTransactionReasonEnum.AMBIGUOUS_MATCH.getCode()
      ),
      ConciliationAnalysisService.EXCLUDED_CARD_RECONCILIATION_MODALITY
    );

    int analyzed = 0;
    int matched = 0;
    int updated = 0;
    int notMatched = 0;
    int valueDivergences = 0;
    int acquirerDivergences = 0;
    int ambiguousMatches = 0;
    int flagUpdated = 0;
    int businessContextUpdated = 0;

    int batchNumber = 0;
    int batchSize = ConciliationAnalysisService.ERP_ACQUIRER_RECONCILIATION_BATCH_SIZE;
    int totalBatches = (int) Math.ceil((double) erpIds.size() / batchSize);

    log.info(
      "📌 Iniciando conciliação ERP x Adquirente (manuais NSU/autorização invertidos). trigger={}, totalErp={}, batchSize={}, totalBatches={}",
      trigger, erpIds.size(), batchSize, totalBatches
    );

    for (int start = 0; start < erpIds.size(); start += batchSize) {
      batchNumber++;
      int end = Math.min(start + batchSize, erpIds.size());
      List<UUID> batchIds = erpIds.subList(start, end);

      List<TransactionErpEntity> erpBatch = transactionErpRepository.findBatchForErpAcquirerReconciliation(batchIds);
      if (erpBatch.isEmpty()) {
        continue;
      }

      // Considera apenas vendas manuais ainda pendentes — as demais já foram tratadas pela etapa principal.
      List<TransactionErpEntity> manualPending = erpBatch.stream()
        .filter(this::isManualCapture)
        .filter(erp -> !conciliationAnalysisService.isExcludedFromCardReconciliation(erp))
        .filter(conciliationAnalysisService::isPendingForErpAcquirerReconciliation)
        .toList();

      if (manualPending.isEmpty()) {
        continue;
      }

      // Candidatas da adquirente cruzando os campos INVERTIDOS (NSU do ERP ↔ autorização
      // da ADQ e vice-versa). É isso que permite achar a venda quando ERP veio trocado.
      List<TransactionAcqEntity> acquirerCandidates = conciliationAnalysisService.findAcquirerCandidatesForBatchSwapped(
        erpBatch,
        false,
        pendingStatuses
      );
      Map<ConciliationAnalysisService.ErpAcquirerIdentityKey, List<TransactionAcqEntity>> acquirersByIdentity =
        conciliationAnalysisService.indexAcquirerCandidates(acquirerCandidates);

      List<TransactionErpEntity> changedErpSales = new ArrayList<>();
      List<TransactionAcqEntity> changedAcquirerSales = new ArrayList<>();
      Set<UUID> changedAcquirerIds = new HashSet<>();

      int batchMatched = 0;

      for (TransactionErpEntity erp : manualPending) {
        analyzed++;

        // Procura candidatas usando a chave com NSU e autorização TROCADOS.
        List<TransactionAcqEntity> identityCandidates = acquirersByIdentity.getOrDefault(
          ConciliationAnalysisService.ErpAcquirerIdentityKey.fromErpSwapped(erp),
          List.of()
        );

        ConciliationAnalysisService.ErpAcquirerMatchResult matchResult =
          conciliationAnalysisService.findBestAcquirerMatchForReconciliation(erp, identityCandidates, true);

        switch (matchResult.status()) {
          case NOT_MATCHED -> {
            notMatched++;
            markManualSwapAttempted(erp, changedErpSales);
          }
          case VALUE_DIVERGENCE -> {
            valueDivergences++;
            markManualSwapAttempted(erp, changedErpSales);
          }
          case ACQUIRER_DIVERGENCE -> {
            acquirerDivergences++;
            markManualSwapAttempted(erp, changedErpSales);
          }
          case AMBIGUOUS -> {
            ambiguousMatches++;
            markManualSwapAttempted(erp, changedErpSales);
          }
          case MATCHED -> {
            TransactionAcqEntity acq = matchResult.acquirerSale();
            matched++;
            batchMatched++;

            // Corrige os dados do ERP com os da adquirente (fonte da verdade).
            // No caso manual, NSU e autorização estavam invertidos; aproveitamos para
            // alinhar também TID, valor e data quando a adquirente os informa.
            correctErpFromAcquirer(erp, acq);

            ConciliationAnalysisService.ErpAcquirerApplyResult applyResult =
              conciliationAnalysisService.applyAcquirerBusinessContext(erp, acq);

            if (applyResult.changed()) {
              updated++;
            }
            if (applyResult.flagUpdated()) {
              flagUpdated++;
            }
            if (applyResult.businessContextUpdated()) {
              businessContextUpdated++;
            }

            changedErpSales.add(erp);
            if (acq.getId() != null && changedAcquirerIds.add(acq.getId())) {
              changedAcquirerSales.add(acq);
            }

            log.info(
              "🔁 Venda manual conciliada com NSU/autorização invertidos. erpId={}, erpNsu={}, erpAuth={}, acqId={}",
              erp.getId(), erp.getNsu(), erp.getAuthorization(), acq.getId()
            );
          }
        }
      }

      if (!changedErpSales.isEmpty()) {
        transactionErpRepository.saveAll(changedErpSales);
      }
      if (!changedAcquirerSales.isEmpty()) {
        transactionAcqRepository.saveAll(changedAcquirerSales);
      }

      entityManager.flush();
      entityManager.clear();

      log.info(
        "🔄 Conciliação manual invertida: batch={}/{}, manuaisPendentes={}, conciliadas={}, totalConciliadas={}",
        batchNumber, totalBatches, manualPending.size(), batchMatched, matched
      );
    }

    log.info(
      "✅ Conciliação ERP x Adquirente (manuais invertidos) finalizada. analisadas={}, conciliadas={}, atualizadas={}, " +
        "naoConciliadas={}, divergValor={}, divergAdquirente={}, ambiguas={}",
      analyzed, matched, updated, notMatched, valueDivergences, acquirerDivergences, ambiguousMatches
    );

    return new ReconcileErpAcquirerResultModel(
      analyzed,
      matched,
      updated,
      0,
      flagUpdated,
      businessContextUpdated,
      notMatched,
      valueDivergences,
      acquirerDivergences,
      ambiguousMatches
    );
  }

  private void markManualSwapAttempted(
    TransactionErpEntity erp,
    List<TransactionErpEntity> changedErpSales
  ) {
    boolean changed = conciliationAnalysisService.applyErpReconciliationStatus(
      erp,
      StatusReconciliationEnum.PENDING,
      StatusTransactionReasonEnum.MANUAL_SWAP_NOT_FOUND
    );
    if (changed) {
      changedErpSales.add(erp);
    }
  }

  private boolean isManualCapture(TransactionErpEntity erp) {
    return erp != null
      && erp.getCapture() != null
      && erp.getCapture().equals(CaptureEnum.MANUAL.getCode());
  }

  /**
   * Corrige os campos de identidade/valor do ERP com os valores da adquirente,
   * que é a fonte da verdade. No fluxo manual, NSU e autorização estavam invertidos;
   * aqui eles passam a refletir os valores corretos da adquirente. TID, valor bruto
   * e data da venda também são alinhados quando a adquirente os informa. Os valores
   * originais do ERP são preservados nas observações para auditoria.
   */
  private void correctErpFromAcquirer(TransactionErpEntity erp, TransactionAcqEntity acq) {
    if (acq == null) {
      return;
    }

    StringBuilder audit = new StringBuilder();

    if (acq.getNsu() != null && !acq.getNsu().equals(erp.getNsu())) {
      audit.append("NSU ").append(erp.getNsu()).append("→").append(acq.getNsu()).append("; ");
      erp.setNsu(acq.getNsu());
    }

    if (isPresent(acq.getAuthorization()) && !equalsIgnoreCaseTrim(acq.getAuthorization(), erp.getAuthorization())) {
      audit.append("autorização ").append(safe(erp.getAuthorization())).append("→").append(acq.getAuthorization().trim()).append("; ");
      erp.setAuthorization(acq.getAuthorization().trim());
    }

    if (isPresent(acq.getTid()) && !equalsIgnoreCaseTrim(acq.getTid(), erp.getTid())) {
      audit.append("TID ").append(safe(erp.getTid())).append("→").append(acq.getTid().trim()).append("; ");
      erp.setTid(acq.getTid().trim());
    }

    if (acq.getGrossValue() != null && (erp.getGrossValue() == null || acq.getGrossValue().compareTo(erp.getGrossValue()) != 0)) {
      audit.append("valor ").append(erp.getGrossValue()).append("→").append(acq.getGrossValue()).append("; ");
      erp.setGrossValue(acq.getGrossValue());
    }

    if (acq.getSaleDate() != null && !acq.getSaleDate().equals(erp.getSaleDate())) {
      audit.append("data ").append(erp.getSaleDate()).append("→").append(acq.getSaleDate()).append("; ");
      erp.setSaleDate(acq.getSaleDate());
    }

    if (!audit.isEmpty()) {
      erp.setObservations(appendObservation(
        erp.getObservations(),
        "Dados corrigidos pela conciliação manual (NSU/autorização invertidos): " + audit.toString().trim()
      ));
    }
  }

  private boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }

  private boolean equalsIgnoreCaseTrim(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return a.trim().equalsIgnoreCase(b.trim());
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String appendObservation(String current, String message) {
    if (message == null || message.isBlank()) {
      return current;
    }
    if (current == null || current.isBlank()) {
      return message;
    }
    return current + " | " + message;
  }
}