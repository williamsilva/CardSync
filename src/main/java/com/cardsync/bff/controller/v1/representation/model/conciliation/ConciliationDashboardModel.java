package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.util.List;

public record ConciliationDashboardModel(
  ConciliationSummaryModel summary,
  List<ConciliationChartPointModel> salesByPeriod,
  ConciliationComparisonModel erpVsAcquirer,
  List<ConciliationChartPointModel> feesByAcquirer,
  List<ConciliationChartPointModel> divergencesByType,
  List<ConciliationAgingModel> pendingAging
) {}
