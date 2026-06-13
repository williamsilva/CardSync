package com.cardsync.core.reconciliation.pipeline;

import com.cardsync.domain.model.enums.ReconciliationPipelineStepEnum;
import com.cardsync.domain.model.enums.ReconciliationPipelineStepStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FinancialReconciliationStepResult {

  private ReconciliationPipelineStepEnum step;
  private ReconciliationPipelineStepStatusEnum status;
  private String message;

  private int analyzed;
  private int reconciled;
  private int partiallyReconciled;
  private int pending;
  private int blocked;
  private int generated;
  private int updated;
  private int divergent;
  private int withoutMatch;

  private OffsetDateTime startedAt;
  private OffsetDateTime finishedAt;

  public boolean completedWithoutBlocking() {
    return status == ReconciliationPipelineStepStatusEnum.COMPLETED && blocked == 0 && divergent == 0;
  }
}
