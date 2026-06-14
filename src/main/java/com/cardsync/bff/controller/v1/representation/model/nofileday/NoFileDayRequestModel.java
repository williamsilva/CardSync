package com.cardsync.bff.controller.v1.representation.model.nofileday;

import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.NoFileDayTypeEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record NoFileDayRequestModel(

  @NotNull
  LocalDate noFileDate,

  @NotBlank
  @Size(max = 255)
  String description,

  @NotNull
  NoFileDayTypeEnum dayType,

  @NotNull
  FileGroupEnum fileGroup,

  UUID bankId,
  UUID acquirerId,
  StatusEnum status
) {
}