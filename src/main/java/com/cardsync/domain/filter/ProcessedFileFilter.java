package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.FileStatusEnum;
import com.cardsync.domain.model.enums.PeriodEnum;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

/**
 * Filtros avançados para a busca de arquivos processados (POST /file-processing/files/search).

 * Segue o padrão do sistema: campos de texto (contains), listas de enums (in),
 * listas de UUID para associações e filtros de período por data.
 */
public record ProcessedFileFilter(

  // Texto (contains, case-insensitive)
  String fileName,
  String typeFile,
  String commercialName,

  // Enums multivalorados
  List<FileStatusEnum> status,
  List<FileGroupEnum> group,

  // Origem (associação originFile) por UUID
  List<String> origins,

  // Período de data do arquivo (date_file) e de importação (date_import)
  PeriodEnum periodDateFile,
  PeriodEnum periodDateImport,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> dateFile,

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  List<String> dateImport
) {
}