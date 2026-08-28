package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SaleSummaryTableFields {

  private final DateFilterService dateFilterService;

  public SaleSummaryTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<SalesSummaryEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("pvNumber",
        FieldSpec.integer("pvNumber", (root, query) -> root.get("pvNumber"))),

      Map.entry("rvNumber",
        FieldSpec.integer("rvNumber", (root, query) -> root.get("rvNumber"))),

      Map.entry("numberCvNsu",
        FieldSpec.integer("numberCvNsu", (root, query) -> root.get("numberCvNsu"))),

      Map.entry("rvDate",
        FieldSpec.localDate("rvDate", (root, query) -> root.get("rvDate"), dateFilterService)),

      Map.entry("grossValue",
        FieldSpec.bigDecimal("grossValue", (root, query) -> root.get("grossValue"))),

      Map.entry("discountValue",
        FieldSpec.bigDecimal("discountValue", (root, query) -> root.get("discountValue"))),

      Map.entry("liquidValue",
        FieldSpec.bigDecimal("liquidValue", (root, query) -> root.get("liquidValue"))),

      Map.entry("modality",
        FieldSpec.enumAsIntegerCode(
          "modality", ModalityEnum.class, ModalityEnum::getCode,
          (root, query) -> root.get("modality")
        )),

      Map.entry("transactionsStatus",
        FieldSpec.enumAsIntegerCode(
          "transactionsStatus", StatusReconciliationEnum.class, StatusReconciliationEnum::getCode,
          (root, query) -> root.get("transactionsStatus")
        )),

      Map.entry("creditOrderStatus",
        FieldSpec.enumAsIntegerCode(
          "creditOrderStatus", StatusReconciliationEnum.class, StatusReconciliationEnum::getCode,
          (root, query) -> root.get("creditOrderStatus")
        )),

      Map.entry("statusPaymentBank",
        FieldSpec.enumAsIntegerCode(
          "statusPaymentBank", StatusPaymentBankEnum.class, StatusPaymentBankEnum::getCode,
          (root, query) -> root.get("statusPaymentBank")
        )),

      Map.entry("flag",
        FieldSpec.joinedUuid("flag", (root, query) -> root.get("flag").get("id"))),

      Map.entry("company",
        FieldSpec.joinedUuid("company", (root, query) -> root.get("company").get("id"))),

      Map.entry("acquirer",
        FieldSpec.joinedUuid("acquirer", (root, query) -> root.get("acquirer").get("id")))
    );
  }
}
