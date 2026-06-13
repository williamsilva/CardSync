package com.cardsync.bff.controller.v1.representation.model.management;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta do dashboard de gerenciamento. Estrutura espelha o contrato de API:
 * sales, payments, fees e debits.
 */
public record ManagementDashboardModel(
  SalesBlock sales,
  SalesBlock payments,
  FeesBlock fees,
  DebitsBlock debits
) {

  /** Usado por sales e payments (mesma estrutura, semântica diferente). */
  public record SalesBlock(
    List<String> labels,
    List<BigDecimal> primarySeries,
    List<BigDecimal> secondarySeries,
    List<SalesRow> rows
  ) {
  }

  public record SalesRow(
    String label,
    long transactions,
    BigDecimal value,
    BigDecimal discount,
    BigDecimal liquid,
    BigDecimal percentage
  ) {
  }

  public record FeesBlock(
    List<String> labels,
    List<BigDecimal> effectiveRateSeries,
    List<BigDecimal> averageRateSeries,
    List<FeesRow> rows
  ) {
  }

  public record FeesRow(
    String label,
    long transactions,
    BigDecimal effectiveRate,
    BigDecimal discount,
    BigDecimal percentage
  ) {
  }

  public record DebitsBlock(
    List<String> labels,
    List<BigDecimal> cancellationSeries,
    List<BigDecimal> feesSeries,
    List<BigDecimal> chargebackSeries,
    DebitSummary fees,
    DebitSummary chargeback,
    DebitSummary cancellation
  ) {
  }

  public record DebitSummary(
    BigDecimal total,
    long quantity,
    BigDecimal average
  ) {
  }
}