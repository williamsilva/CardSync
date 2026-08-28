package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.RequestNoticeEntity;
import com.cardsync.domain.model.enums.ChargebackRequestStatusEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChargeBackRequestsTableFields {

  private final DateFilterService dateFilterService;

  public ChargeBackRequestsTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  /**
   * Não inclui "requestReason" aqui — cada motivo agrupa vários códigos brutos legados (ver
   * ChargebackRequestReasonEnum) e o modelo genérico de FieldSpec só converte 1 valor recebido
   * em 1 valor de comparação, não em N códigos por bucket. Expandido à parte em
   * AdjustmentChargeBackRequestsSpecs.
   */
  public Map<String, FieldSpec<RequestNoticeEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("cvNsu",
        FieldSpec.longNumber("cvNsu", (root, query) -> root.get("nsu"))),

      Map.entry("authorization",
        FieldSpec.string("authorization", (root, query) -> root.get("authorization"))),

      Map.entry("transactionValue",
        FieldSpec.bigDecimal("transactionValue", (root, query) -> root.get("transactionValue"))),

      Map.entry("saleDate",
        FieldSpec.localDate("saleDate", (root, query) -> root.get("saleDate"), dateFilterService)),

      Map.entry("deadline",
        FieldSpec.localDate("deadline", (root, query) -> root.get("deadline"), dateFilterService)),

      Map.entry("adjustmentStatus",
        FieldSpec.enumAsIntegerCode(
          "adjustmentStatus", ChargebackRequestStatusEnum.class, ChargebackRequestStatusEnum::getCode,
          (root, query) -> root.get("requestStatus")
        )),

      // Modalidade não é coluna própria — vem do resumo de vendas vinculado (mesmo path de
      // ChargeBackRequestsAdvancedFields).
      Map.entry("modality",
        FieldSpec.enumAsIntegerCode(
          "modality", ModalityEnum.class, ModalityEnum::getCode,
          (root, query) -> root.get("salesSummary").get("modality")
        )),

      Map.entry("flag",
        FieldSpec.joinedUuid("flag", (root, query) -> root.get("flag").get("id"))),

      Map.entry("company",
        FieldSpec.joinedUuid("company", (root, query) -> root.get("company").get("id"))),

      Map.entry("acquirer",
        FieldSpec.joinedUuid("acquirer", (root, query) -> root.get("acquirer").get("id"))),

      Map.entry("establishment",
        FieldSpec.joinedUuid("establishment", (root, query) -> root.get("establishment").get("id")))
    );
  }
}
