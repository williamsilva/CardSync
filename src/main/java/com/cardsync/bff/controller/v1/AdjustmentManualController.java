package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.input.AdjustmentManualInput;
import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentMinimalModel;
import com.cardsync.core.reconciliation.summary.AdjustmentManualService;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.model.AdjustmentEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/adjustments")
public class AdjustmentManualController {

  private final AdjustmentManualService adjustmentManualService;

  @PostMapping("/manual")
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.FileProcessing.CanProcess
  public AdjustmentMinimalModel createManual(@Valid @RequestBody AdjustmentManualInput body) {
    AdjustmentEntity saved = adjustmentManualService.create(body);

    return AdjustmentMinimalModel.builder()
      .id(saved.getId())
      .rvNumberOriginal(saved.getRvNumberOriginal())
      .adjustmentValue(saved.getAdjustmentValue())
      .creditDate(saved.getCreditDate())
      .build();
  }
}
