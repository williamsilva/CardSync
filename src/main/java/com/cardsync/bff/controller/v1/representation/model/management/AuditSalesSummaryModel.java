package com.cardsync.bff.controller.v1.representation.model.management;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumo de vendas para auditoria do dashboard.

 * - summary: total por dia, consolidando TODAS as adquirentes.
 * - acquirerDetails: para cada adquirente, suas linhas por dia.

 * Espelha as interfaces do front: AuditSalesSummaryModel / AuditSalesDetail / AuditSaleRow.
 */
public record AuditSalesSummaryModel(
  List<AuditSaleRow> summary,
  List<AuditSalesDetail> acquirerDetails
) {

  public record AuditSaleRow(
    String date,
    BigDecimal value,
    long cvCount
  ) {
  }

  public record AuditSalesDetail(
    String acquirerName,
    List<AuditSaleRow> rows
  ) {
  }
}