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
          (root, query) -> root.join("establishment", JoinType.LEFT).get("id"))),

      // Aliases abaixo: cancellation-list e tariffs-list usam nomes de field= diferentes entre
      // si (e diferentes dos já cadastrados acima) pro mesmo dado — mantidos os dois pra não
      // precisar tocar nos dois componentes Angular por uma diferença só de nome.
      Map.entry("cvNsu",
        FieldSpec.longNumber(
          "cvNsu",
          (root, query) -> root.get("nsu"))),

      // cancellation-list usa field="rvNumber", tariffs-list usa field="rvNumberAdjustment" —
      // ambos são o mesmo AdjustmentEntity.rvNumberAdjustment (já cadastrado acima como
      // "rvAdjustment", que nenhuma das duas telas usa de fato).
      Map.entry("rvNumber",
        FieldSpec.integer(
          "rvNumber",
          (root, query) -> root.get("rvNumberAdjustment"))),

      Map.entry("rvNumberAdjustment",
        FieldSpec.integer(
          "rvNumberAdjustment",
          (root, query) -> root.get("rvNumberAdjustment"))),

      Map.entry("reason",
        FieldSpec.integer(
          "reason",
          (root, query) -> root.get("adjustmentReason"))),

      Map.entry("status",
        FieldSpec.integer(
          "status",
          (root, query) -> root.get("adjustmentStatus"))),

      // Só em cancellation-list: dados da venda original cancelada, não do próprio ajuste
      // (ver row.transaction?.saleDate / row.transaction?.grossValue no template).
      Map.entry("saleDate",
        FieldSpec.offsetDateTime(
          "saleDate",
          (root, query) -> root.join("transaction", JoinType.LEFT).get("saleDate"),
          dateFilterService)),

      Map.entry("saleValue",
        FieldSpec.bigDecimal(
          "saleValue",
          (root, query) -> root.join("transaction", JoinType.LEFT).get("grossValue")))
    );
  }
}