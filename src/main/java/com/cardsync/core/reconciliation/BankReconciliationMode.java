package com.cardsync.core.reconciliation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BankReconciliationMode {

  CREDIT_ORDER_FIRST("Ordem de crédito primeiro, com fallback por parcelas"),
  CREDIT_ORDER_ONLY("Somente ordem de crédito"),
  INSTALLMENT_ONLY("Somente parcelas"),
  INSTALLMENT_FALLBACK("Parcelas como fallback");

  private final String description;

  public boolean shouldTryCreditOrders() {
    return this == CREDIT_ORDER_FIRST || this == CREDIT_ORDER_ONLY || this == INSTALLMENT_FALLBACK;
  }

  public boolean shouldTryInstallmentsAfterCreditOrders() {
    return this == CREDIT_ORDER_FIRST || this == INSTALLMENT_FALLBACK;
  }

  public boolean shouldTryInstallmentsFirst() {
    return this == INSTALLMENT_ONLY;
  }
}
