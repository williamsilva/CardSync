package com.cardsync.bff.controller.v1.representation.input;

import com.cardsync.domain.model.enums.AcquirerFileTypeEnum;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.NoFileDayTypeEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record NoFileDayInput(

  @NotNull
  LocalDate noFileDate,

  @NotBlank
  @Size(max = 255)
  String description,

  @NotNull
  NoFileDayTypeEnum dayType,

  @NotNull
  FileGroupEnum fileGroup,

  UUID bankingDomicileId,
  UUID acquirerId,
  AcquirerFileTypeEnum acquirerFileType,
  StatusEnum status
) {
}
