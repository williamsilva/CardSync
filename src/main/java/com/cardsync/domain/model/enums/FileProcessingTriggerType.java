package com.cardsync.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FileProcessingTriggerType {

  MANUAL("MANUAL", "Execução manual"),

  SCHEDULER_ERP("SCHEDULER_ERP", "Agendamento ERP"),
  SCHEDULER_REDE("SCHEDULER_REDE", "Agendamento Rede"),
  SCHEDULER_BANK("SCHEDULER_BANK", "Agendamento bancário"),
  SCHEDULER_SEQUENTIAL_ERP(
    "SCHEDULER_SEQUENTIAL_ERP","Job sequencial - processamento ERP"),
  SCHEDULER_SEQUENTIAL_REDE(
    "SCHEDULER_SEQUENTIAL_REDE","Job sequencial - processamento Rede/adquirente"),
  SCHEDULER_SEQUENTIAL_CIELO(
    "SCHEDULER_SEQUENTIAL_CIELO","Job sequencial - processamento Cielo/adquirente"),
  SCHEDULER_SEQUENTIAL_BANK(
    "SCHEDULER_SEQUENTIAL_BANK","Job sequencial - processamento Banco/CNAB"),
  SCHEDULER_ERP_ACQUIRER_RECONCILIATION(
    "SCHEDULER_ERP_ACQUIRER_RECONCILIATION","Agendamento conciliação ERP x adquirente"),
  SCHEDULER_ERP_ACQUIRER_FEE_RECONCILIATION(
    "SCHEDULER_ERP_ACQUIRER_FEE_RECONCILIATION","Agendamento conciliação de taxas ERP x adquirente"),
  SCHEDULER_SEQUENTIAL_ERP_ACQUIRER_RECONCILIATION(
    "SCHEDULER_SEQUENTIAL_ERP_ACQUIRER_RECONCILIATION","Job sequencial - conciliação ERP x adquirente"),
  SCHEDULER_SEQUENTIAL_ERP_ACQUIRER_FEE_RECONCILIATION(
    "SCHEDULER_SEQUENTIAL_ERP_ACQUIRER_FEE_RECONCILIATION","Job sequencial - conciliação de taxas ERP x adquirente"),
  SCHEDULER_SEQUENTIAL_ACQUIRER_SALE_SUMMARY_RECONCILIATION(
    "SCHEDULER_SEQUENTIAL_ACQUIRER_SALE_SUMMARY_RECONCILIATION", "Job sequencial - conciliação venda adquirente x resumo"),
  SCHEDULER_SEQUENTIAL_SALES_SUMMARY_CREDIT_ORDER_RECONCILIATION(
    "SCHEDULER_SEQUENTIAL_SALES_SUMMARY_CREDIT_ORDER_RECONCILIATION", "Job sequencial - conciliação resumo x ordem de pagamento"),
  SCHEDULER_SEQUENTIAL_CREDIT_ORDER_BANK_RELEASE_RECONCILIATION(
    "SCHEDULER_SEQUENTIAL_CREDIT_ORDER_BANK_RELEASE_RECONCILIATION",
    "Job sequencial - conciliação ordem de pagamento x lançamento bancário"
  );

  private final String code;
  private final String description;
}