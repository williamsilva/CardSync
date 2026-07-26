package com.cardsync.core.reconciliation.summary;

import com.cardsync.bff.controller.v1.mapper.model.SaleSummaryModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualInput;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualResult;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderSkipReason;
import com.cardsync.bff.controller.v1.representation.model.transactions.SaleSummaryModel;
import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.domain.filter.SaleSummaryFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.infrastructure.repository.spec.SaleSummarySpecs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditOrderManualService {

  private static final int RECONCILIATION_STATUS_PENDING = 1;

  private final SaleSummarySpecs saleSummarySpecs;
  private final CreditOrderRepository creditOrderRepository;
  private final SalesSummaryRepository salesSummaryRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final SaleSummaryModelAssembler saleSummaryModelAssembler;
  private final ReconciliationSettingsService reconciliationSettingsService;

  @Transactional(readOnly = true)
  public Page<SaleSummaryModel> searchPendingSummaries(Pageable pageable, ListQueryDto<SaleSummaryFilter> query) {
    int days = reconciliationSettingsService.getCreditOrderPendingDays();
    LocalDate cutoffDate = LocalDate.now().minusDays(days);
    LocalDate yesterday  = LocalDate.now().minusDays(1);
    LocalDate monthAgo   = yesterday.minusMonths(1);

    Specification<SalesSummaryEntity> filterSpec = saleSummarySpecs.fromQueryForPendingCreditOrdersTotals(query, cutoffDate, yesterday, monthAgo);
    Specification<SalesSummaryEntity> dataSpec   = saleSummarySpecs.fromQueryForPendingCreditOrders(query, cutoffDate, yesterday, monthAgo);

    long total = salesSummaryRepository.count(filterSpec);
    List<SaleSummaryModel> content = total == 0
      ? List.of()
      : salesSummaryRepository.findAll(dataSpec, pageable).stream()
          .map(saleSummaryModelAssembler::toModel)
          .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional
  public CreditOrderManualResult create(CreditOrderManualInput input) {
    List<UUID> createdIds = new ArrayList<>();
    List<CreditOrderSkipReason> skippedReasons = new ArrayList<>();

    for (UUID summaryId : input.summaryIds()) {
      SalesSummaryEntity summary = salesSummaryRepository.findById(summaryId).orElse(null);
      if (summary == null) {
        skippedReasons.add(new CreditOrderSkipReason(null, "SUMMARY_NOT_FOUND", 0));
        log.warn("⚠️ Resumo não encontrado: {}", summaryId);
        continue;
      }

      try {
        int installmentTotal = transactionAcqRepository.findMaxInstallmentBySalesSummaryId(summaryId);
        Set<Integer> existing = creditOrderRepository.findInstallmentNumbersBySalesSummaryId(summaryId);

        List<Integer> missingInstallments = new ArrayList<>();
        for (int i = 1; i <= installmentTotal; i++) {
          if (!existing.contains(i)) {
            missingInstallments.add(i);
          }
        }

        if (missingInstallments.isEmpty()) {
          skippedReasons.add(new CreditOrderSkipReason(String.valueOf(summary.getRvNumber()), "ALL_INSTALLMENTS_COVERED", installmentTotal));
          continue;
        }

        LocalDate baseDate = summary.getFirstInstallmentCreditDate() != null
          ? summary.getFirstInstallmentCreditDate()
          : summary.getRvDate();

        // Fecha TODAS as lacunas do resumo nesta chamada, não só a primeira parcela faltante —
        // antes, um resumo com múltiplas parcelas ausentes exigia uma chamada manual por parcela.
        int createdForThisSummary = 0;
        for (int installmentNumber : missingInstallments) {
          LocalDate nextReleaseDate = baseDate != null ? baseDate.plusMonths(installmentNumber - 1) : null;
          if (nextReleaseDate != null && nextReleaseDate.isAfter(LocalDate.now().minusDays(1))) {
            skippedReasons.add(new CreditOrderSkipReason(String.valueOf(summary.getRvNumber()), "FUTURE_RELEASE_DATE", installmentNumber));
            log.info("⏭️ Parcela {}/{} ignorada — vencimento futuro: {}", installmentNumber, installmentTotal, nextReleaseDate);
            // Datas crescem com o número da parcela — as seguintes também seriam futuras.
            break;
          }

          CreditOrderEntity co = buildCreditOrder(summary, installmentNumber, installmentTotal);
          co = creditOrderRepository.save(co);
          createdIds.add(co.getId());
          createdForThisSummary++;

          log.info("✅ Ordem de crédito manual criada: id={}, summaryId={}, parcela={}/{}, releaseDate={}, releaseValue={}",
            co.getId(), summaryId, installmentNumber, installmentTotal, co.getReleaseDate(), co.getReleaseValue());
        }

        if (createdForThisSummary > 0) {
          updateSummaryCreditOrderStatus(summary, existing.size() + createdForThisSummary, installmentTotal);
        }

      } catch (IllegalStateException e) {
        skippedReasons.add(new CreditOrderSkipReason(String.valueOf(summary.getRvNumber()), "UNEXPECTED_ERROR", 0));
        log.warn("⚠️ Falha ao criar ordem de crédito para summary {}: {}", summaryId, e.getMessage());
      }
    }

    return new CreditOrderManualResult(createdIds.size(), skippedReasons.size(), createdIds, skippedReasons);
  }

  private CreditOrderEntity buildCreditOrder(SalesSummaryEntity summary, int installmentNumber, int installmentTotal) {
    BigDecimal gross = orZero(summary.getGrossValue());
    BigDecimal discount = orZero(summary.getDiscountValue());
    BigDecimal liquid = orZero(summary.getLiquidValue());

    BigDecimal grossPer = installmentTotal > 1
      ? gross.divide(BigDecimal.valueOf(installmentTotal), 2, RoundingMode.DOWN) : gross;
    BigDecimal discountPer = installmentTotal > 1
      ? discount.divide(BigDecimal.valueOf(installmentTotal), 2, RoundingMode.DOWN) : discount;
    BigDecimal releaseValue = installmentTotal > 1
      ? liquid.divide(BigDecimal.valueOf(installmentTotal), 2, RoundingMode.DOWN) : liquid;

    LocalDate baseDate = summary.getFirstInstallmentCreditDate() != null
      ? summary.getFirstInstallmentCreditDate()
      : summary.getRvDate();
    LocalDate releaseDate = baseDate != null ? baseDate.plusMonths(installmentNumber - 1) : null;

    CreditOrderEntity co = new CreditOrderEntity();
    co.setPvCentralizer(summary.getPvNumber());
    co.setOriginalPvNumber(summary.getPvNumber());
    co.setRvNumber(summary.getRvNumber());
    co.setRvDate(summary.getRvDate());
    co.setSalesSummary(summary);
    co.setAcquirer(summary.getAcquirer());
    co.setCompany(summary.getCompany());
    co.setFlag(summary.getFlag());
    co.setBankingDomicile(summary.getBankingDomicile());
    co.setInstallmentNumber(installmentNumber);
    co.setInstallmentTotal(installmentTotal);
    co.setGrossRvValue(grossPer);
    co.setDiscountRateValue(discountPer);
    co.setReleaseValue(releaseValue);
    co.setReleaseDate(releaseDate);
    co.setCreditOrderDate(baseDate);
    co.setRecordType("MANUAL_GENERATED");
    co.setLaunchType("MANUAL");
    co.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    co.setSalesSummaryStatus(StatusReconciliationEnum.PENDING);
    co.setReconciliationStatus(RECONCILIATION_STATUS_PENDING);
    return co;
  }

  private void updateSummaryCreditOrderStatus(SalesSummaryEntity summary, int newCount, int installmentTotal) {
    StatusReconciliationEnum newStatus = newCount >= installmentTotal
      ? StatusReconciliationEnum.RECONCILED
      : StatusReconciliationEnum.PARTIALLY_RECONCILED;

    if (summary.getCreditOrderStatus() != newStatus) {
      summary.setCreditOrderStatus(newStatus);
      log.info("📊 creditOrderStatus {} → {}", summary.getId(), newStatus);
    }
  }

  private static BigDecimal orZero(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
