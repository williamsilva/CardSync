package com.cardsync.core.reconciliation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BankReconciliationMatchType {

  CREDIT_ORDER("Ordem de crédito"),
  CREDIT_ORDER_GROUP("Grupo de ordens de crédito"),
  INSTALLMENT("Parcela"),
  INSTALLMENT_GROUP("Grupo de parcelas"),
  MANUAL("Manual");

  private final String description;

  public static BankReconciliationMatchType creditOrderByCount(int count) {
    return count <= 1 ? CREDIT_ORDER : CREDIT_ORDER_GROUP;
  }

  public static BankReconciliationMatchType installmentByCount(int count) {
    return count <= 1 ? INSTALLMENT : INSTALLMENT_GROUP;
  }
}
