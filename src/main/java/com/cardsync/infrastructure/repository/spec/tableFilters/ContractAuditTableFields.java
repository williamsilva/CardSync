package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.ContractAuditEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ContractAuditTableFields {

  private final DateFilterService dateFilterService;

  public ContractAuditTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

    public Map<String, FieldSpec<ContractAuditEntity, ?>> table() {
      return Map.ofEntries(
        // Filtros diretos da Venda
        Map.entry("company",
          FieldSpec.joinedUuid(
            "company",
            (root, query) -> root.join("company", JoinType.LEFT).get("id")
          )),

        Map.entry("establishment",
          FieldSpec.integer(
            "establishment",
            (root, query) -> root.join("establishment", JoinType.LEFT).get("pvNumber")
          )),

        Map.entry("authorization",
          FieldSpec.string(
            "authorization",
            (root, query) -> root.get("authorization"))),

        Map.entry("cvNsu",
          FieldSpec.longNumber(
            "cvNsu",
            (root, query) -> root.get("nsu"))),

        Map.entry("modality",
          FieldSpec.enumAsIntegerCode(
            "modality",
            ModalityEnum.class,
            ModalityEnum::getCode,
            (root, query) -> root.get("modality")
          )
        ),

        Map.entry("flag",
          FieldSpec.joinedUuid(
            "flag",
            (root, query) -> root.join("flag", JoinType.LEFT).get("id")
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

        Map.entry("appliedFeeValue",
          FieldSpec.bigDecimal(
            "appliedFeeValue",
            (root, query) -> root.get("rateAcquirer"))),

        Map.entry("liquidValue",
          FieldSpec.bigDecimal(
            "liquidValue",
            (root, query) -> root.get("liquidValue"))),

        Map.entry("differenceValue",
          FieldSpec.bigDecimal(
            "differenceValue",
            (root, query) -> root.get("differenceValue"))),

        Map.entry("rateContract",
          FieldSpec.bigDecimal(
            "rateContract",
            (root, query) -> root.get("rateContract"))),

        // Filtros aninhados transactionErp
        Map.entry("liquidValueErp",
          FieldSpec.bigDecimal(
            "liquidValueErp",
            (root, query) -> root.join("transactionErp", JoinType.LEFT).get("liquidValue"))),

        // Filtros aninhados transactionAcq
        Map.entry("saleDate",
          FieldSpec.offsetDateTime(
            "saleDate",
            (root, query) -> root.join("transactionAcq", JoinType.LEFT).get("saleDate"),
            dateFilterService
          )),

        Map.entry("capture",
          FieldSpec.enumAsIntegerCode(
            "capture",
            CaptureEnum.class,
            CaptureEnum::getCode,
            (root, query) -> root.join("transactionAcq", JoinType.LEFT).get("capture")
          )
        ),

        Map.entry("installment",
          FieldSpec.integer(
            "installment",
            (root, query) -> root.join("transactionAcq", JoinType.LEFT).get("installment")))
      );
    }
}
