package com.cardsync.core.file.acquirerreport.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha do "Relatório de Pagamentos" exportado pela adquirente (ex.: Itaú/Rede), usado na
 * importação em lote de ordens de crédito manuais (ver AcquirerPaymentReportCsvReader e
 * CreditOrderManualService#importFromAcquirerReport). Os valores aqui são os REAIS já
 * liquidados pela adquirente — diferente da fórmula de aproximação usada na criação manual
 * sem arquivo (CreditOrderManualService#buildCreditOrder).
 */
public record AcquirerPaymentReportRow(
  String fileName,
  int lineNumber,
  Integer rvNumber,
  Integer pvNumber,
  Integer installmentNumber,
  Integer installmentTotal,
  LocalDate releaseDate,
  LocalDate originalDueDate,
  BigDecimal releaseValue,
  BigDecimal grossValue,
  BigDecimal discountValue,
  String status
) {
}
