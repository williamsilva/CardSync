package com.cardsync.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReconciliationPipelineStepStatusEnum {

  NOT_STARTED("Não iniciado"),
  RUNNING("Executando"),
  COMPLETED("Concluído"),
  SKIPPED("Ignorado"),
  BLOCKED("Bloqueado"),
  FAILED("Falhou");

  private final String description;
}
