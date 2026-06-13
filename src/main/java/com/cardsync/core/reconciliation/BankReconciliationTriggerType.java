package com.cardsync.core.reconciliation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BankReconciliationTriggerType {

  MANUAL("MANUAL", "Execução manual"),
  SCHEDULER_BANK_RECONCILIATION(
    "SCHEDULER_BANK_RECONCILIATION",
    "Agendamento conciliação banco x adquirente"
  );

  private final String code;
  private final String description;
}
