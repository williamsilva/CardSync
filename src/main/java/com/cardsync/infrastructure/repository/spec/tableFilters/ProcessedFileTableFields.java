package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.ProcessedFileEntity;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.FileStatusEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProcessedFileTableFields {

  private final DateFilterService dateFilterService;

  public ProcessedFileTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<ProcessedFileEntity, ?>> table() {
    return Map.ofEntries(

      // Texto
      Map.entry("fileName",
        FieldSpec.string(
          "fileName",
          (root, query) -> root.get("file"))),

      Map.entry("typeFile",
        FieldSpec.string(
          "typeFile",
          (root, query) -> root.get("typeFile"))),

      Map.entry("commercialName",
        FieldSpec.string(
          "commercialName",
          (root, query) -> root.get("commercialName"))),

      // Enums armazenados como STRING (@Enumerated(STRING)) — comparados por nome/código
      Map.entry("status",
        FieldSpec.enumCodeByNameOrCode(
          "status",
          FileStatusEnum.class,
          null,
          (root, query) -> root.get("status"))),

      Map.entry("group",
        FieldSpec.enumCodeByNameOrCode(
          "group",
          FileGroupEnum.class,
          null,
          (root, query) -> root.get("group"))),

      // Datas — filtro de coluna
      Map.entry("dateFile",
        FieldSpec.localDate(
          "dateFile",
          (root, query) -> root.get("dateFile"),
          dateFilterService)),

      Map.entry("dateImport",
        FieldSpec.offsetDateTime(
          "dateImport",
          (root, query) -> root.get("dateImport"),
          dateFilterService)),

      Map.entry("dateProcessing",
        FieldSpec.offsetDateTime(
          "dateProcessing",
          (root, query) -> root.get("dateProcessing"),
          dateFilterService)),

      // Origem (associação por UUID)
      Map.entry("origin",
        FieldSpec.joinedUuid(
          "origin",
          (root, query) -> root.join("originFile", JoinType.LEFT).get("id")))
    );
  }
}