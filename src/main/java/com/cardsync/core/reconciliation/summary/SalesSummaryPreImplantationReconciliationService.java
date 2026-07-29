package com.cardsync.core.reconciliation.summary;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Backfill/ferramenta de análise para o gap descrito ao investigar por que muitas ordens de
 * crédito pós go-live nunca ficam elegíveis pra conciliação bancária (Etapa 6 do matcher,
 * {@code findEligibleIdsGroupedByCompanyForBankReconciliation}, exige
 * {@code salesSummaryStatus = RECONCILED}): o SalesSummary da venda original é anterior à
 * implantação ({@code rvDate < implantationDate}) e por isso NUNCA é reavaliado por
 * {@link SalesSummaryCreditOrderReconciliationService#reconcilePending} — nem no fluxo normal,
 * nem no backfill de lookback já existente (ambas as consultas exigem {@code rvDate >=
 * implantationDate}) — mesmo quando alguma parcela (CreditOrder) só vence/libera bem depois do
 * go-live e já tem lançamento bancário compatível esperando.
 *
 * {@link #preview()} nunca grava nada; só {@link #apply} executa de fato, reaproveitando
 * {@link SalesSummaryCreditOrderReconciliationService#reconcilePreImplantation} — mesma
 * classificação/geração sintética/atualização em lote do fluxo normal, só mudando a origem dos
 * SalesSummary avaliados.
 */
@Service
@RequiredArgsConstructor
public class SalesSummaryPreImplantationReconciliationService {

  private static final List<Integer> ELIGIBLE_TRANSACTION_SUMMARY_STATUSES = List.of(
    StatusReconciliationEnum.RECONCILED.getCode(),
    StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode()
  );

  private static final List<Integer> PENDING_SUMMARY_CREDIT_ORDER_STATUSES = List.of(
    StatusReconciliationEnum.PENDING.getCode(),
    StatusReconciliationEnum.PARTIALLY_RECONCILED.getCode()
  );

  private static final int BATCH_SIZE = 1_000;

  private final ImplantationDateProvider implantationDateProvider;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final SalesSummaryRepository salesSummaryRepository;
  private final SalesSummaryCreditOrderReconciliationService salesSummaryCreditOrderReconciliationService;

  @Transactional(readOnly = true)
  public SalesSummaryPreImplantationPreviewResult preview() {
    boolean reprocess = reconciliationSettingsService.isReprocessSalesSummaryCreditOrder();
    LocalDate implantationDate = implantationDateProvider.get();

    List<SalesSummaryCreditOrderStats> stats = salesSummaryRepository.findStatsForSalesSummaryCreditOrderReconciliationPreImplantation(
      reprocess, ELIGIBLE_TRANSACTION_SUMMARY_STATUSES, PENDING_SUMMARY_CREDIT_ORDER_STATUSES, implantationDate
    );

    int wouldReconcile = 0;
    int wouldPartiallyReconcile = 0;
    BigDecimal totalGrossValue = BigDecimal.ZERO;
    List<UUID> withoutOrdersIds = new ArrayList<>();

    for (SalesSummaryCreditOrderStats row : stats) {
      totalGrossValue = totalGrossValue.add(row.getGrossValue() != null ? row.getGrossValue() : BigDecimal.ZERO);
      long ordersCount = row.creditOrdersCountSafe();
      if (ordersCount == 0) {
        withoutOrdersIds.add(row.getSalesSummaryId());
      } else if (row.isFullyReconciled()) {
        wouldReconcile++;
      } else {
        wouldPartiallyReconcile++;
      }
    }

    int wouldGenerateSynthetic = countEligibleForSyntheticGeneration(withoutOrdersIds);
    int wouldRemainPending = withoutOrdersIds.size() - wouldGenerateSynthetic;

    return new SalesSummaryPreImplantationPreviewResult(
      stats.size(), wouldReconcile, wouldPartiallyReconcile, wouldGenerateSynthetic, wouldRemainPending, totalGrossValue
    );
  }

  @Transactional
  public SalesSummaryCreditOrderReconciliationResult apply() {
    return salesSummaryCreditOrderReconciliationService.reconcilePreImplantation(FinancialReconciliationTriggerType.MANUAL);
  }

  private int countEligibleForSyntheticGeneration(List<UUID> summaryIds) {
    if (summaryIds.isEmpty()) return 0;

    int eligible = 0;
    for (int start = 0; start < summaryIds.size(); start += BATCH_SIZE) {
      List<UUID> batch = summaryIds.subList(start, Math.min(start + BATCH_SIZE, summaryIds.size()));
      for (SalesSummaryEntity summary : salesSummaryRepository.findBatchForSalesSummaryCreditOrderReconciliation(batch)) {
        if (salesSummaryCreditOrderReconciliationService.shouldGenerateSyntheticCreditOrder(summary)) {
          eligible++;
        }
      }
    }
    return eligible;
  }
}
