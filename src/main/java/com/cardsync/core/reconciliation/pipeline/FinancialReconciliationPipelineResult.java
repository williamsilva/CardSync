package com.cardsync.core.reconciliation.pipeline;

import com.cardsync.domain.model.enums.FinancialReconciliationTriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FinancialReconciliationPipelineResult {

  private FinancialReconciliationTriggerType trigger;

  @Builder.Default
  private List<FinancialReconciliationStepResult> steps = new ArrayList<>();

  private OffsetDateTime startedAt;
  private OffsetDateTime finishedAt;

  public void addStep(FinancialReconciliationStepResult step) {
    if (steps == null) {
      steps = new ArrayList<>();
    }
    steps.add(step);
  }
}
