package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.AcquirerFileTypeEnum;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.NoFileDayTypeEnum;
import com.cardsync.domain.model.enums.StatusEnum;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record NoFileDayFilter(
  UUID id,

  String description,

  LocalDate noFileDate,

  List<StatusEnum> statusEnum,
  List<FileGroupEnum> fileGroup,
  List<NoFileDayTypeEnum> dayType,
  List<AcquirerFileTypeEnum> acquirerFileType,

  List<String> bankingDomiciles,
  List<String> companies,
  List<String> acquirers
) {
}