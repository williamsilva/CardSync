package com.cardsync.bff.controller.v1.representation.input;

import com.cardsync.domain.model.enums.ModalityEnum;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ContractRateInput(
  @NotNull ModalityEnum modality,
  @NotNull BigDecimal rate,
  @NotNull Integer paymentTermDays,
  BigDecimal rateEcommerce,
  Integer paymentTermDaysEcommerce
) {}
