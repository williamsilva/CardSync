package com.cardsync.bff.controller.v1.representation.model.conciliation;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ManualBankReconciliationRequest(
  @NotNull UUID releaseBankId,
  @NotEmpty List<@NotNull UUID> creditOrderIds,
  /**
   * Obrigatório quando a soma das ordens não bate com o valor do lançamento (fora da tolerância
   * configurada) — ex.: lançamento mistura vendas anteriores à implantação (sem CreditOrder no
   * sistema) com vendas atuais. Validado de novo no service (defesa em profundidade).
   */
  @Size(max = 500) String divergenceReason
) {}
