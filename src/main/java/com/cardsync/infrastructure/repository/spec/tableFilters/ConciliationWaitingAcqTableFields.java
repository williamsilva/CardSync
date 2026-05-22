package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConciliationWaitingAcqTableFields {

  private final DateFilterService dateFilterService;

  public ConciliationWaitingAcqTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<TransactionAcqEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("company",
        FieldSpec.joinedUuid(
          "company",
          (root, query) -> root.join("company", JoinType.LEFT).get("id")
        )),

      Map.entry("saleDate",
        FieldSpec.offsetDateTime(
          "saleDate",
          (root, query) -> root.get("saleDate"),
          dateFilterService
        )),

      Map.entry("nsu",
        FieldSpec.longNumber(
          "nsu",
          (root, query) -> root.get("nsu"))),

      Map.entry("cvNsu",
        FieldSpec.longNumber(
          "cvNsu",
          (root, query) -> root.get("nsu"))),

      Map.entry("authorization",
        FieldSpec.string(
          "authorization",
          (root, query) -> root.get("authorization"))),

      Map.entry("establishment",
        FieldSpec.joinedUuid(
          "establishment",
          (root, query) -> root.join("establishment", JoinType.LEFT).get("id")
        )),

      Map.entry("acquirer",
        FieldSpec.joinedUuid(
          "acquirer",
          (root, query) -> root.join("acquirer", JoinType.LEFT).get("id")
        )),

      Map.entry("grossValue",
        FieldSpec.bigDecimal(
          "grossValue",
          (root, query) -> root.get("grossValue"))),

      Map.entry("liquidValue",
        FieldSpec.bigDecimal(
          "liquidValue",
          (root, query) -> root.get("liquidValue"))),

      Map.entry("installment",
        FieldSpec.integer(
          "installment",
          (root, query) -> root.get("installment"))),

      Map.entry("flag",
        FieldSpec.joinedUuid(
          "flag",
          (root, query) -> root.join("flag", JoinType.LEFT).get("id")
        )),

      Map.entry("modality",
        FieldSpec.enumAsIntegerCode(
          "modality",
          ModalityEnum.class,
          ModalityEnum::getCode,
          (root, query) -> root.get("modality")
        )
      ),

      Map.entry("capture",
        FieldSpec.enumAsIntegerCode(
          "capture",
          CaptureEnum.class,
          CaptureEnum::getCode,
          (root, query) -> root.get("capture")
        )
      ),

      Map.entry("statusTransactionReason",
        FieldSpec.integer(
          "statusTransactionReason",
          (root, query) -> root.get("statusTransactionReason")))
    );
  }
}
