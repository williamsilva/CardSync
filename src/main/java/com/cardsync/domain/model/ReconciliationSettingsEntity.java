package com.cardsync.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "cs_reconciliation_settings")
public class ReconciliationSettingsEntity extends AuditableEntityBase {

  @Column(name = "erp_acquirer_previous_days_lookback", nullable = false)
  private int erpAcquirerPreviousDaysLookback = 0;

  @Column(name = "erp_acquirer_future_days_lookback", nullable = false)
  private int erpAcquirerFutureDaysLookback = 0;

  @Column(name = "reconciliation_lookback_months", nullable = false)
  private int reconciliationLookbackMonths = 1;

  @Column(name = "credit_order_pending_days", nullable = false)
  private int creditOrderPendingDays = 30;

  // ── Flags de habilitação de etapas (ordem = esteira de conciliação) ──────

  /** Etapa 1 — ERP x Adquirente: se false, a etapa é pulada na esteira. */
  @Column(name = "enabled_erp_acquirer", nullable = false)
  private boolean enabledErpAcquirer = true;

  /** Etapa 2 — Resumo de vendas x TransactionAcq: se false, a etapa é pulada. */
  @Column(name = "enabled_sales_summary_transactions", nullable = false)
  private boolean enabledSalesSummaryTransactions = true;

  /** Etapa 3 — Cancelamentos da adquirente: se false, a etapa é pulada. */
  @Column(name = "enabled_acquirer_sale_cancellations", nullable = false)
  private boolean enabledAcquirerSaleCancellations = true;

  /** Etapa 4 — Taxas ERP x Adquirente: se false, a etapa é pulada. */
  @Column(name = "enabled_erp_acquirer_fees", nullable = false)
  private boolean enabledErpAcquirerFees = true;

  /** Etapa 5 — Venda ADQ x Resumo: se false, a etapa é pulada. */
  @Column(name = "enabled_acquirer_sale_summary", nullable = false)
  private boolean enabledAcquirerSaleSummary = true;

  /** Etapa 6 — Resumo x Ordem de Pagamento: se false, a etapa é pulada. */
  @Column(name = "enabled_sales_summary_credit_order", nullable = false)
  private boolean enabledSalesSummaryCreditOrder = true;

  /** Etapa 7 — Ordem de Pagamento x Banco: se false, a etapa é pulada. */
  @Column(name = "enabled_bank_acquirer", nullable = false)
  private boolean enabledBankAcquirer = true;

  // ── Flags de reprocessamento (ordem = esteira de conciliação) ─────────────

  /** Etapa 1 — ERP x Adquirente (vendas): reprocessa vendas já conciliadas. */
  @Column(name = "reprocess_erp_acquirer_sales", nullable = false)
  private boolean reprocessErpAcquirerSales = false;

  /** Etapa 2 — Resumo de vendas x TransactionAcq: reprocessa resumos já conciliados. */
  @Column(name = "reprocess_sales_summary_transactions", nullable = false)
  private boolean reprocessSalesSummaryTransactions = false;

  /** Etapa 3 — Cancelamentos da adquirente: reprocessa vendas já canceladas. */
  @Column(name = "reprocess_acquirer_sale_cancellations", nullable = false)
  private boolean reprocessAcquirerSaleCancellations = false;

  /** Etapa 4 — Taxas ERP x Adquirente: reprocessa taxas já processadas. */
  @Column(name = "reprocess_erp_acquirer_fees", nullable = false)
  private boolean reprocessErpAcquirerFees = false;

  /** Etapa 5 — Venda ADQ x Resumo: reprocessa resumos já conciliados. */
  @Column(name = "reprocess_acquirer_sale_summary", nullable = false)
  private boolean reprocessAcquirerSaleSummary = false;

  /** Etapa 6 — Resumo x Ordem de Pagamento: reprocessa resumos já conciliados com ordem. */
  @Column(name = "reprocess_sales_summary_credit_order", nullable = false)
  private boolean reprocessSalesSummaryCreditOrder = false;

  /** Etapa 7 — Ordem de Pagamento x Banco: reprocessa ordens e lançamentos já conciliados. */
  @Column(name = "reprocess_bank_acquirer", nullable = false)
  private boolean reprocessBankAcquirer = false;

  // ── Parâmetros de tolerância ───────────────────────────────────────────────

  /** Dias que o lançamento bancário pode ser ANTERIOR à ordem de crédito. */
  @Column(name = "date_tolerance_days_before", nullable = false)
  private int dateToleranceDaysBefore = 5;

  /** Dias que o lançamento bancário pode ser POSTERIOR à ordem de crédito. */
  @Column(name = "date_tolerance_days_after", nullable = false)
  private int dateToleranceDaysAfter = 10;

  @Column(name = "value_tolerance", nullable = false, precision = 10, scale = 4)
  private BigDecimal valueTolerance = new BigDecimal("0.05");

  @Column(name = "bank_mark_not_reconciled_after_days", nullable = false)
  private int bankMarkNotReconciledAfterDays = 3;
}
