package com.cardsync.core.conciliation;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconciliationSettingsModel;
import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconciliationSettingsRequest;
import com.cardsync.domain.model.ReconciliationSettingsEntity;
import com.cardsync.domain.repository.ReconciliationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReconciliationSettingsService {

  private final ReconciliationSettingsRepository repository;

  @Transactional(readOnly = true)
  public ReconciliationSettingsModel getSettings() {
    return repository.findFirstBy()
      .map(s -> new ReconciliationSettingsModel(
        s.getErpAcquirerPreviousDaysLookback(),
        s.getErpAcquirerFutureDaysLookback(),
        s.getReconciliationLookbackMonths(),
        s.getCreditOrderPendingDays(),
        s.isEnabledErpAcquirer(),
        s.isEnabledSalesSummaryTransactions(),
        s.isEnabledAcquirerSaleCancellations(),
        s.isEnabledErpAcquirerFees(),
        s.isEnabledAcquirerSaleSummary(),
        s.isEnabledSalesSummaryCreditOrder(),
        s.isEnabledBankAcquirer(),
        s.isReprocessErpAcquirerSales(),
        s.isReprocessSalesSummaryTransactions(),
        s.isReprocessAcquirerSaleCancellations(),
        s.isReprocessErpAcquirerFees(),
        s.isReprocessAcquirerSaleSummary(),
        s.isReprocessSalesSummaryCreditOrder(),
        s.isReprocessBankAcquirer(),
        s.getDateToleranceDaysBefore(),
        s.getDateToleranceDaysAfter(),
        s.getValueTolerance(),
        s.getBankMarkNotReconciledAfterDays()))
      .orElse(new ReconciliationSettingsModel(0, 0, 1, 30,
        true, true, true, true, true, true, true,
        false, false, false, false, false, false, false,
        5, 10, new BigDecimal("0.05"), 3));
  }

  // ── Campos numéricos ───────────────────────────────────────────────────────

  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public int getErpAcquirerPreviousDaysLookback() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getErpAcquirerPreviousDaysLookback)
      .orElse(0);
  }

  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public int getErpAcquirerFutureDaysLookback() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getErpAcquirerFutureDaysLookback)
      .orElse(0);
  }

  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public int getReconciliationLookbackMonths() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getReconciliationLookbackMonths)
      .orElse(1);
  }

  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public int getCreditOrderPendingDays() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getCreditOrderPendingDays)
      .orElse(30);
  }

  // ── Flags de habilitação de etapas (ordem = esteira de conciliação) ──────

  /** Etapa 1 — ERP x Adquirente. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isEnabledErpAcquirer() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isEnabledErpAcquirer)
      .orElse(true);
  }

  /** Etapa 2 — Resumo de vendas x TransactionAcq. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isEnabledSalesSummaryTransactions() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isEnabledSalesSummaryTransactions)
      .orElse(true);
  }

  /** Etapa 3 — Cancelamentos da adquirente. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isEnabledAcquirerSaleCancellations() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isEnabledAcquirerSaleCancellations)
      .orElse(true);
  }

  /** Etapa 4 — Taxas ERP x Adquirente. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isEnabledErpAcquirerFees() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isEnabledErpAcquirerFees)
      .orElse(true);
  }

  /** Etapa 5 — Venda ADQ x Resumo de vendas. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isEnabledAcquirerSaleSummary() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isEnabledAcquirerSaleSummary)
      .orElse(true);
  }

  /** Etapa 6 — Resumo x Ordem de pagamento. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isEnabledSalesSummaryCreditOrder() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isEnabledSalesSummaryCreditOrder)
      .orElse(true);
  }

  /** Etapa 7 — Ordem de pagamento x Lançamento bancário. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isEnabledBankAcquirer() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isEnabledBankAcquirer)
      .orElse(true);
  }

  // ── Flags de reprocessamento (ordem = esteira de conciliação) ─────────────

  /** Etapa 1 — ERP x Adquirente (vendas). */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isReprocessErpAcquirerSales() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isReprocessErpAcquirerSales)
      .orElse(false);
  }

  /** Etapa 2 — Resumo de vendas x TransactionAcq. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isReprocessSalesSummaryTransactions() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isReprocessSalesSummaryTransactions)
      .orElse(false);
  }

  /** Etapa 3 — Cancelamentos da adquirente. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isReprocessAcquirerSaleCancellations() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isReprocessAcquirerSaleCancellations)
      .orElse(false);
  }

  /** Etapa 4 — Taxas ERP x Adquirente. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isReprocessErpAcquirerFees() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isReprocessErpAcquirerFees)
      .orElse(false);
  }

  /** Etapa 5 — Venda ADQ x Resumo de vendas. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isReprocessAcquirerSaleSummary() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isReprocessAcquirerSaleSummary)
      .orElse(false);
  }

  /** Etapa 6 — Resumo de vendas x Ordem de pagamento. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isReprocessSalesSummaryCreditOrder() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isReprocessSalesSummaryCreditOrder)
      .orElse(false);
  }

  /** Etapa 7 — Ordem de pagamento x Lançamento bancário. */
  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public boolean isReprocessBankAcquirer() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::isReprocessBankAcquirer)
      .orElse(false);
  }

  // ── Parâmetros de tolerância ───────────────────────────────────────────────

  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public int getDateToleranceDaysBefore() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getDateToleranceDaysBefore)
      .orElse(5);
  }

  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public int getDateToleranceDaysAfter() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getDateToleranceDaysAfter)
      .orElse(10);
  }

  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public BigDecimal getValueTolerance() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getValueTolerance)
      .orElse(new BigDecimal("0.05"));
  }

  @Cacheable(value = "reconciliation-settings", key = "#root.methodName")
  @Transactional(readOnly = true)
  public int getBankMarkNotReconciledAfterDays() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getBankMarkNotReconciledAfterDays)
      .orElse(3);
  }

  // ── Atualização ────────────────────────────────────────────────────────────

  @CacheEvict(value = "reconciliation-settings", allEntries = true)
  @Transactional
  public ReconciliationSettingsModel update(ReconciliationSettingsRequest request) {
    ReconciliationSettingsEntity settings = repository.findFirstBy()
      .orElseGet(ReconciliationSettingsEntity::new);
    settings.setErpAcquirerPreviousDaysLookback(request.erpAcquirerPreviousDaysLookback());
    settings.setErpAcquirerFutureDaysLookback(request.erpAcquirerFutureDaysLookback());
    settings.setReconciliationLookbackMonths(request.reconciliationLookbackMonths());
    settings.setCreditOrderPendingDays(request.creditOrderPendingDays());
    settings.setEnabledErpAcquirer(request.enabledErpAcquirer());
    settings.setEnabledSalesSummaryTransactions(request.enabledSalesSummaryTransactions());
    settings.setEnabledAcquirerSaleCancellations(request.enabledAcquirerSaleCancellations());
    settings.setEnabledErpAcquirerFees(request.enabledErpAcquirerFees());
    settings.setEnabledAcquirerSaleSummary(request.enabledAcquirerSaleSummary());
    settings.setEnabledSalesSummaryCreditOrder(request.enabledSalesSummaryCreditOrder());
    settings.setEnabledBankAcquirer(request.enabledBankAcquirer());
    settings.setReprocessErpAcquirerSales(request.reprocessErpAcquirerSales());
    settings.setReprocessSalesSummaryTransactions(request.reprocessSalesSummaryTransactions());
    settings.setReprocessAcquirerSaleCancellations(request.reprocessAcquirerSaleCancellations());
    settings.setReprocessErpAcquirerFees(request.reprocessErpAcquirerFees());
    settings.setReprocessAcquirerSaleSummary(request.reprocessAcquirerSaleSummary());
    settings.setReprocessSalesSummaryCreditOrder(request.reprocessSalesSummaryCreditOrder());
    settings.setReprocessBankAcquirer(request.reprocessBankAcquirer());
    settings.setDateToleranceDaysBefore(request.dateToleranceDaysBefore());
    settings.setDateToleranceDaysAfter(request.dateToleranceDaysAfter());
    settings.setValueTolerance(request.valueTolerance());
    settings.setBankMarkNotReconciledAfterDays(request.bankMarkNotReconciledAfterDays());
    settings = repository.save(settings);
    return new ReconciliationSettingsModel(
      settings.getErpAcquirerPreviousDaysLookback(),
      settings.getErpAcquirerFutureDaysLookback(),
      settings.getReconciliationLookbackMonths(),
      settings.getCreditOrderPendingDays(),
      settings.isEnabledErpAcquirer(),
      settings.isEnabledSalesSummaryTransactions(),
      settings.isEnabledAcquirerSaleCancellations(),
      settings.isEnabledErpAcquirerFees(),
      settings.isEnabledAcquirerSaleSummary(),
      settings.isEnabledSalesSummaryCreditOrder(),
      settings.isEnabledBankAcquirer(),
      settings.isReprocessErpAcquirerSales(),
      settings.isReprocessSalesSummaryTransactions(),
      settings.isReprocessAcquirerSaleCancellations(),
      settings.isReprocessErpAcquirerFees(),
      settings.isReprocessAcquirerSaleSummary(),
      settings.isReprocessSalesSummaryCreditOrder(),
      settings.isReprocessBankAcquirer(),
      settings.getDateToleranceDaysBefore(),
      settings.getDateToleranceDaysAfter(),
      settings.getValueTolerance(),
      settings.getBankMarkNotReconciledAfterDays());
  }
}
