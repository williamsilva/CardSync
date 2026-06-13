package com.cardsync.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FinancialReconciliationTriggerType {

  MANUAL("Manual"),
  SCHEDULER_SEQUENTIAL_JOB("Agendamento sequencial CardSync"),
  SCHEDULER_FINANCIAL_PIPELINE("Agendamento da esteira financeira"),
  SCHEDULER_ACQUIRER_SALE_SUMMARY("Agendamento Venda ADQ x resumo"),
  SCHEDULER_SALES_SUMMARY_CREDIT_ORDER("Agendamento Resumo x ordem"),
  SCHEDULER_CREDIT_ORDER_BANK_RELEASE("Agendamento Ordem x banco");

  private final String description;
}
