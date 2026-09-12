package com.cardsync.core.reconciliation.cancellation;

import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AcquirerSaleCancellationResult {

  private FinancialReconciliationTriggerType trigger;

  private int adjustmentsAnalyzed;
  private int fullCancellationsIdentified;
  private int acquirerSalesCanceled;
  private int erpSalesCanceled;
  private int acquirerInstallmentsCanceled;
  private int erpInstallmentsCanceled;
  private int skippedPartialCancellations;
  private int skippedWithoutTransaction;
  private int skippedAlreadyCanceled;

  /**
   * Vendas (ERP e/ou ADQ) que estavam MANUALLY_RECONCILED (resolvidas manualmente na tela
   * "Aguardando conciliação") e foram canceladas por um ajuste da adquirente. Diferente da
   * Etapa 1 (ERP x Adquirente), aqui o cancelamento NÃO é bloqueado - é uma informação nova e
   * autoritativa vinda da adquirente (a venda foi de fato cancelada/chargebacada no mundo real),
   * então deve prevalecer mesmo sobre um pareamento manual. Esta métrica só dá visibilidade a
   * esse cenário raro/sensível no histórico da esteira (ver análise profunda 2026-09-12).
   */
  private int manuallyReconciledOverridden;

  private OffsetDateTime startedAt;
  private OffsetDateTime finishedAt;
}
