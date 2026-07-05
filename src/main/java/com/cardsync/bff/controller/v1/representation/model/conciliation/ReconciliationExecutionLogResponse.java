package com.cardsync.bff.controller.v1.representation.model.conciliation;

import com.cardsync.core.reconciliation.pipeline.FinancialReconciliationStepResult;

import java.time.OffsetDateTime;
import java.util.List;

public record ReconciliationExecutionLogResponse(
    String id,
    String trigger,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    String overallStatus,
    Integer totalAnalyzed,
    Integer totalReconciled,
    Integer totalPending,
    List<FinancialReconciliationStepResult> steps
) {}
