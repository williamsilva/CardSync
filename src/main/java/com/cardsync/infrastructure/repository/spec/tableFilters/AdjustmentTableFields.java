package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AdjustmentTableFields {

  private final DateFilterService dateFilterService;

  public AdjustmentTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<AdjustmentEntity, ?>> table() {
    return Map.ofEntries(

      // Numérico — NSU usa equals/startsWith, nunca CAST LIKE bilateral
      Map.entry("nsu",
        FieldSpec.longNumber(
          "nsu",
          (root, query) -> root.get("nsu"))),

      Map.entry("rvAdjustment",
        FieldSpec.integer(
          "rvNumberAdjustment",
          (root, query) -> root.get("rvNumberAdjustment"))),

      // Texto
      Map.entry("authorization",
        FieldSpec.string(
          "authorization",
          (root, query) -> root.get("authorization"))),

      // Motivo do ajuste (código inteiro — ex: 20=POS Inativo/Conectividade, 1=Antecipação)
      Map.entry("adjustmentReason",
        FieldSpec.integer(
          "adjustmentReason",
          (root, query) -> root.get("adjustmentReason"))),

      // Status do ajuste
      Map.entry("adjustmentStatus",
        FieldSpec.integer(
          "adjustmentStatus",
          (root, query) -> root.get("adjustmentStatus"))),

      // Valores monetários
      Map.entry("adjustmentValue",
        FieldSpec.bigDecimal(
          "adjustmentValue",
          (root, query) -> root.get("adjustmentValue"))),

      Map.entry("grossValue",
        FieldSpec.bigDecimal(
          "grossValue",
          (root, query) -> root.get("grossValue"))),

      Map.entry("liquidValue",
        FieldSpec.bigDecimal(
          "liquidValue",
          (root, query) -> root.get("liquidValue"))),

      Map.entry("discountValue",
        FieldSpec.bigDecimal(
          "discountValue",
          (root, query) -> root.get("discountValue"))),

      // Datas — filtro de coluna do PrimeNG DataTable
      Map.entry("adjustmentDate",
        FieldSpec.localDate(
          "adjustmentDate",
          (root, query) -> root.get("adjustmentDate"),
          dateFilterService)),

      Map.entry("creditDate",
        FieldSpec.localDate(
          "creditDate",
          (root, query) -> root.get("creditDate"),
          dateFilterService)),

      Map.entry("releaseDate",
        FieldSpec.localDate(
          "releaseDate",
          (root, query) -> root.get("releaseDate"),
          dateFilterService)),

      // Joins de entidades (filtro por UUID)
      Map.entry("company",
        FieldSpec.joinedUuid(
          "company",
          (root, query) -> root.join("company", JoinType.LEFT).get("id"))),

      Map.entry("acquirer",
        FieldSpec.joinedUuid(
          "acquirer",
          (root, query) -> root.join("acquirer", JoinType.LEFT).get("id"))),

      Map.entry("flag",
        FieldSpec.joinedUuid(
          "flag",
          (root, query) -> root.join("rvFlagAdjustment", JoinType.LEFT).get("id"))),

      Map.entry("establishment",
        FieldSpec.joinedUuid(
          "establishment",
          (root, query) -> root.join("establishment", JoinType.LEFT).get("id")))
    );
  }
}