package com.cardsync.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReconciliationPipelineStepEnum {

  ERP_ACQUIRER(1, "ADQ x ERP"),
  SALES_SUMMARY_TRANSACTION(2, "Resumo de vendas x TransactionAcq"),
  ACQUIRER_SALE_CANCELLATION(3, "Cancelamentos informados pela adquirente"),
  ACQUIRER_SALE_ADJUSTMENTS(4, "Ajustes das vendas"),
  ACQUIRER_SALE_SUMMARY(5, "Venda ADQ x resumo de vendas"),
  SALES_SUMMARY_CREDIT_ORDER(6, "Resumo de vendas x ordem de pagamento"),
  CREDIT_ORDER_BANK_RELEASE(7, "Ordem de pagamento x lançamento bancário");

  private final int order;
  private final String description;
}
