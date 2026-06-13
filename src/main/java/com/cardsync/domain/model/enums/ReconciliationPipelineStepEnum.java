package com.cardsync.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReconciliationPipelineStepEnum {

  ERP_ACQUIRER(1, "ADQ x ERP"),
  ACQUIRER_SALE_CANCELLATION(2, "Cancelamentos informados pela adquirente"),
  ACQUIRER_SALE_ADJUSTMENTS(3, "Ajustes das vendas"),
  ACQUIRER_SALE_SUMMARY(4, "Venda ADQ x resumo de vendas"),
  SALES_SUMMARY_CREDIT_ORDER(5, "Resumo de vendas x ordem de pagamento"),
  CREDIT_ORDER_BANK_RELEASE(6, "Ordem de pagamento x lançamento bancário");

  private final int order;
  private final String description;
}
