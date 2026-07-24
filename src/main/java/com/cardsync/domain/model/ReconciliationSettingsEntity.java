package com.cardsync.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

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

  /**
   * Teto de centavos para a busca de subconjunto (subset-sum) por programação dinâmica na
   * conciliação Banco x Ordem de Crédito/Parcela (Etapa 7). A DP aloca arrays proporcionais ao
   * alvo em centavos; acima deste limite o subconjunto não é tentado e o lançamento permanece
   * pendente mesmo com ordens de crédito compatíveis disponíveis. Ver
   * BankReconciliationMatcher.selectByValue. Antes fixo em application.yml
   * (file-processing.reconciliation.subset-dp-max-cents); movido para cá para ser ajustável sem
   * redeploy.
   */
  @Column(name = "subset_dp_max_cents", nullable = false)
  private long subsetDpMaxCents = 50_000_000L;

  // ── Rigidez do matching Banco x Ordem de Crédito / Parcela (Etapa 7) ──────
  // Default false nos três = comportamento legado (campo opcional/coringa quando nulo/
  // desconhecido em qualquer lado). Ver ReconciliationMatchContext.MatchStrictness.

  /** Bandeira (flag) obrigatória no matching — hoje coringa quando nula em qualquer lado. */
  @Column(name = "flag_match_required", nullable = false)
  private boolean flagMatchRequired = false;

  /** Estabelecimento (por número de PV) obrigatório no matching — hoje coringa quando nulo em qualquer lado. */
  @Column(name = "establishment_match_required", nullable = false)
  private boolean establishmentMatchRequired = false;

  /** Modalidade de pagamento (débito/crédito) obrigatória no matching — hoje UNKNOWN age como coringa. */
  @Column(name = "payment_kind_match_required", nullable = false)
  private boolean paymentKindMatchRequired = false;

  // ── Implantação e marcação de lançamentos como legado ─────────────────────

  /**
   * Data em que o CardSync entrou em operação (go-live). Datas anteriores são
   * tratadas como completas nas agendas operacionais e filtradas nas listagens,
   * pois não havia expectativa de arquivos.
   */
  @Column(name = "go_live_date", nullable = false)
  private LocalDate goLiveDate = LocalDate.of(2024, 7, 1);

  /**
   * Meses após o go-live em que a marcação manual de lançamentos bancários como
   * legado permanece disponível. Após o período, o botão é ocultado.
   */
  @Column(name = "legacy_marking_months", nullable = false)
  private int legacyMarkingMonths = 12;
}
