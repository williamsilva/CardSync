package com.cardsync.core.reconciliation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;

import java.math.BigDecimal;

/**
 * Snapshot das configurações de conciliação usadas pra achar ordens de crédito candidatas de um
 * lançamento — mesmo contrato usado pelo matcher automático ({@link BankReconciliationService}),
 * reaproveitado pelas ferramentas de análise (divergência pré-implantação, legado sem ordem).
 */
record CreditOrderMatchSettings(
  int toleranceDaysBefore,
  int toleranceDaysAfter,
  BigDecimal valueTolerance,
  ReconciliationMatchContext.MatchStrictness strictness
) {
  static CreditOrderMatchSettings from(ReconciliationSettingsService settingsService) {
    return new CreditOrderMatchSettings(
      settingsService.getDateToleranceDaysBefore(),
      settingsService.getDateToleranceDaysAfter(),
      settingsService.getValueTolerance(),
      new ReconciliationMatchContext.MatchStrictness(
        settingsService.isFlagMatchRequired(),
        settingsService.isEstablishmentMatchRequired(),
        settingsService.isPaymentKindMatchRequired()
      )
    );
  }
}
