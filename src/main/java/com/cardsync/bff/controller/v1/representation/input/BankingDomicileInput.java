package com.cardsync.bff.controller.v1.representation.input;

import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record BankingDomicileInput(

  StatusEnum status,
  String agencyDigit,
  String accountDigit,

  @NotNull
  Integer agency,

  @NotNull
  Integer currentAccount,
  @NotNull
  LocalDate accountOpeningDate,
  LocalDate accountClosingDate,
  @NotNull
  Boolean expectsFile,
  @NotNull
  UUID bankId,
  @NotNull
  UUID companyId
) {}
