package com.cardsync.bff.controller.v1.representation.model.management;

import com.cardsync.domain.model.enums.PeriodEnum;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

/**
 * Filtros do dashboard de gerenciamento.

 * - companyIds/acquirerIds/flagIds: vazios ou null = todas.
 * - modalities: "CREDIT", "DEBIT", "PIX"; vazio ou null = todas.
 * - periodSaleDate + saleDate: filtro de data no MESMO padrão dos specs do sistema.
 *   periodSaleDate define o tipo (DAY, START, END, MONTH, YEAR, INTERVAL) e saleDate
 *   carrega o(s) valor(es) de data (YYYY-MM-DD, ou YYYY-MM para MONTH, YYYY para YEAR,
 *   e dois valores para INTERVAL).
 * - groupBy: dimensão de agrupamento por seção (COMPANY, ACQUIRER, MODALITY, FLAG, DATE).
 */
public record ManagementDashboardRequest(
  List<String> companyIds,
  List<String> acquirerIds,
  List<String> flagIds,
  List<String> modalities,
  PeriodEnum periodSaleDate,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> saleDate,

  GroupBy groupBy
) {
  public record GroupBy(
    String sales,
    String payments,
    String fees,
    String debits
  ) {
  }
}