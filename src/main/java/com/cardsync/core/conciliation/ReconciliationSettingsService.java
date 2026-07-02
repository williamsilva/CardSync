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
        s.isReprocessErpAcquirerSales(),
        s.isReprocessSalesSummaryTransactions(),
        s.isReprocessAcquirerSaleCancellations(),
        s.isReprocessErpAcquirerFees(),
        s.isReprocessAcquirerSaleSummary(),
        s.isReprocessSalesSummaryCreditOrder(),
        s.isReprocessBankAcquirer(),
        s.getDateToleranceDays(),
        s.getValueTolerance(),
        s.getBankMarkNotReconciledAfterDays()))
      .orElse(new ReconciliationSettingsModel(0, 0, 1, 30,
        false, false, false, false, false, false, false,
        10, new BigDecimal("0.05"), 3));
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
  public int getDateToleranceDays() {
    return repository.findFirstBy()
      .map(ReconciliationSettingsEntity::getDateToleranceDays)
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
    settings.setReprocessErpAcquirerSales(request.reprocessErpAcquirerSales());
    settings.setReprocessSalesSummaryTransactions(request.reprocessSalesSummaryTransactions());
    settings.setReprocessAcquirerSaleCancellations(request.reprocessAcquirerSaleCancellations());
    settings.setReprocessErpAcquirerFees(request.reprocessErpAcquirerFees());
    settings.setReprocessAcquirerSaleSummary(request.reprocessAcquirerSaleSummary());
    settings.setReprocessSalesSummaryCreditOrder(request.reprocessSalesSummaryCreditOrder());
    settings.setReprocessBankAcquirer(request.reprocessBankAcquirer());
    settings.setDateToleranceDays(request.dateToleranceDays());
    settings.setValueTolerance(request.valueTolerance());
    settings.setBankMarkNotReconciledAfterDays(request.bankMarkNotReconciledAfterDays());
    settings = repository.save(settings);
    return new ReconciliationSettingsModel(
      settings.getErpAcquirerPreviousDaysLookback(),
      settings.getErpAcquirerFutureDaysLookback(),
      settings.getReconciliationLookbackMonths(),
      settings.getCreditOrderPendingDays(),
      settings.isReprocessErpAcquirerSales(),
      settings.isReprocessSalesSummaryTransactions(),
      settings.isReprocessAcquirerSaleCancellations(),
      settings.isReprocessErpAcquirerFees(),
      settings.isReprocessAcquirerSaleSummary(),
      settings.isReprocessSalesSummaryCreditOrder(),
      settings.isReprocessBankAcquirer(),
      settings.getDateToleranceDays(),
      settings.getValueTolerance(),
      settings.getBankMarkNotReconciledAfterDays());
  }
}
