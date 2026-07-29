package com.cardsync.core.reconciliation.summary;

import java.math.BigDecimal;

public record SalesSummaryPreImplantationPreviewResult(
  int summariesAnalyzed,
  int wouldReconcile,
  int wouldPartiallyReconcile,
  int wouldGenerateSynthetic,
  int wouldRemainPending,
  BigDecimal totalGrossValueAnalyzed
) {}
