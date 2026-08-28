package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.NoFileDayTypeEnum;
import com.cardsync.domain.model.enums.StatusEnum;

import java.util.List;
import java.util.UUID;

/**
 * Espelha NoFileDayAdvancedFilters (no-file-day.filters.ts). O registro anterior tinha
 * noFileDate (LocalDate único) e companies/acquirers/bankingDomiciles que o painel nunca
 * enviou — nunca bateu com o que buildAdvancedFilters() realmente manda (noFileDateFrom/To,
 * um intervalo de datas puro, sem período).
 */
public record NoFileDayFilter(
  UUID id,
  String description,
  String noFileDateFrom,
  String noFileDateTo,
  List<StatusEnum> statusEnum,
  List<NoFileDayTypeEnum> dayType,
  List<FileGroupEnum> fileGroup
) {
}
