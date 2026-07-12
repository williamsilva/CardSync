package com.cardsync.core.conciliation.analysis;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconcileErpAcquirerResultModel;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.model.enums.StatusTransactionReasonEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.repository.TransactionErpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

  private final PlatformTransactionManager transactionManager;
  private final ImplantationDateProvider implantationDateProvider;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final TransactionErpRepository transactionErpRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final AcquirerRepository acquirerRepository;
  private final ConciliationAnalysisService conciliationAnalysisService;

  @Transactional
  public ReconcileErpAcquirerResultModel reconcileRedeManualSwapped() {
    return reconcileRedeManualSwapped("MANUAL");
  }

  @Transactional
  public ReconcileErpAcquirerResultModel reconcileRedeManualSwapped(String trigger) {
    // Esta etapa nunca reprocessa vendas já conciliadas: ela só atua sobre o que
    // sobrou pendente após a conciliação principal.
    List<Integer> pendingStatuses = conciliationAnalysisService.erpAcquirerPendingStatusCodes();

    OffsetDateTime implantationDate = implantationDateProvider.get().atStartOfDay().atOffset(ZoneOffset.UTC);
    OffsetDateTime lookbackDate = LocalDate.now()
      .minusMonths(reconciliationSettingsService.getReconciliationLookbackMonths())
      .atStartOfDay().atOffset(ZoneOffset.UTC);

    UUID redeAcquirerId = acquirerRepository.findByFileIdentifierIgnoreCase("REDE")
      .map(AcquirerEntity::getId)
      .orElse(null);
    if (redeAcquirerId == null) {
      log.warn("⚠️ Adquirente REDE não encontrada na base, conciliação manual swap ignorada.");
      return new ReconcileErpAcquirerResultModel(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    List<UUID> erpIds = transactionErpRepository.findRedeErpIdsForManualSwapReconciliation(
      pendingStatuses,
      CaptureEnum.MANUAL.getCode(),
      List.of(
        StatusTransactionReasonEnum.CV_NOT_FOUND_ADQ.getCode(),
        StatusTransactionReasonEnum.VALUE_MISMATCH.getCode(),
        StatusTransactionReasonEnum.ACQUIRER_MISMATCH.getCode(),
        StatusTransactionReasonEnum.AMBIGUOUS_MATCH.getCode()
      ),
      ConciliationAnalysisService.EXCLUDED_CARD_RECONCILIATION_MODALITY,
      implantationDate,
      lookbackDate
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
      "📌 Iniciando conciliação ERP Vendas Rede x Adquirente Rede (manuais NSU/autorização invertidos). trigger={}, totalErp={}, batchSize={}, totalBatches={}",
      trigger, erpIds.size(), batchSize, totalBatches
    );

    TransactionTemplate batchTx = new TransactionTemplate(transactionManager);
    batchTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    for (int start = 0; start < erpIds.size(); start += batchSize) {
      batchNumber++;
      final int end = Math.min(start + batchSize, erpIds.size());
      final List<UUID> batchIds = erpIds.subList(start, end);
      final int currentBatch = batchNumber;

      // [0]=analyzed [1]=matched [2]=updated [3]=notMatched [4]=valDiv [5]=acqDiv
      // [6]=ambiguous [7]=flagUpd [8]=ctxUpd [9]=batchMatched [10]=manualPendingSize
      int[] r = batchTx.execute(status -> {
        List<TransactionErpEntity> erpBatch = transactionErpRepository.findRedeErpBatchForReconciliation(batchIds);
        if (erpBatch.isEmpty()) {
          return null;
        }

        // Considera apenas vendas manuais ainda pendentes — as demais já foram tratadas pela etapa principal.
        List<TransactionErpEntity> manualPending = erpBatch.stream()
          .filter(this::isManualCapture)
          .filter(erp -> !conciliationAnalysisService.isExcludedFromCardReconciliation(erp))
          .filter(conciliationAnalysisService::isPendingForErpAcquirerReconciliation)
          .toList();

        if (manualPending.isEmpty()) {
          return null;
        }

        // Candidatas da adquirente cruzando os campos INVERTIDOS (NSU do ERP ↔ autorização
        // da ADQ e vice-versa). É isso que permite achar a venda quando ERP veio trocado.
        List<TransactionAcqEntity> acquirerCandidates = conciliationAnalysisService.findAcquirerCandidatesForBatchSwapped(
          erpBatch,
          false,
          pendingStatuses,
          lookbackDate,
          redeAcquirerId
        );
        Map<ConciliationAnalysisService.ErpAcquirerIdentityKey, List<TransactionAcqEntity>> acquirersByIdentity =
          conciliationAnalysisService.indexAcquirerCandidates(acquirerCandidates);

        List<TransactionErpEntity> changedErpSales = new ArrayList<>();
        List<TransactionAcqEntity> changedAcquirerSales = new ArrayList<>();
        Set<UUID> changedAcquirerIds = new HashSet<>();

        int[] counts = new int[11];
        counts[10] = manualPending.size();

        for (TransactionErpEntity erp : manualPending) {
          counts[0]++;  // analyzed

          // Procura candidatas usando a chave com NSU e autorização TROCADOS.
          List<TransactionAcqEntity> identityCandidates = acquirersByIdentity.getOrDefault(
            ConciliationAnalysisService.ErpAcquirerIdentityKey.fromErpSwapped(erp),
            List.of()
          );

          ConciliationAnalysisService.ErpAcquirerMatchResult matchResult =
            conciliationAnalysisService.findBestAcquirerMatchForReconciliation(erp, identityCandidates, true);

          switch (matchResult.status()) {
            case NOT_MATCHED -> {
              counts[3]++;
              markManualSwapAttempted(erp, changedErpSales);
            }
            case VALUE_DIVERGENCE -> {
              counts[4]++;
              markManualSwapAttempted(erp, changedErpSales);
            }
            case ACQUIRER_DIVERGENCE -> {
              counts[5]++;
              markManualSwapAttempted(erp, changedErpSales);
            }
            case AMBIGUOUS -> {
              counts[6]++;
              markManualSwapAttempted(erp, changedErpSales);
            }
            case MATCHED -> {
              TransactionAcqEntity acq = matchResult.acquirerSale();
              counts[1]++;   // matched
              counts[9]++;   // batchMatched

              // Corrige os dados do ERP com os da adquirente (fonte da verdade).
              // No caso manual, NSU e autorização estavam invertidos; aproveitamos para
              // alinhar também TID, valor e data quando a adquirente os informa.
              correctErpFromAcquirer(erp, acq);

              ConciliationAnalysisService.ErpAcquirerApplyResult applyResult =
                conciliationAnalysisService.applyAcquirerBusinessContext(erp, acq);

              if (applyResult.changed()) {
                counts[2]++;
              }
              if (applyResult.flagUpdated()) {
                counts[7]++;
              }
              if (applyResult.businessContextUpdated()) {
                counts[8]++;
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

        return counts;
      });

      if (r == null) {
        continue;
      }

      analyzed += r[0];
      matched += r[1];
      updated += r[2];
      notMatched += r[3];
      valueDivergences += r[4];
      acquirerDivergences += r[5];
      ambiguousMatches += r[6];
      flagUpdated += r[7];
      businessContextUpdated += r[8];

      log.info(
        "🔄 Conciliação manual invertida: batch={}/{}, manuaisPendentes={}, conciliadas={}, totalConciliadas={}",
        currentBatch, totalBatches, r[10], r[9], matched
      );
    }

    log.info(
      "✅ Conciliação ERP Vendas Rede x Adquirente Rede (manuais invertidos) finalizada. analisadas={}, conciliadas={}, atualizadas={}, " +
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

  private void markManualSwapAttempted(TransactionErpEntity erp, List<TransactionErpEntity> changedErpSales) {
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