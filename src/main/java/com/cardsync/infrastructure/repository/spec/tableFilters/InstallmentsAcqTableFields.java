package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InstallmentsAcqTableFields {

  private final DateFilterService dateFilterService;

  public InstallmentsAcqTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<InstallmentAcqEntity, ?>> table() {
    return Map.ofEntries(
      Map.entry("grossValue",
        FieldSpec.bigDecimal(
          "grossValue",
          (root, query) -> root.get("grossValue"))),

      Map.entry("discountValue",
        FieldSpec.bigDecimal(
          "discountValue",
          (root, query) -> root.get("discountValue"))),

      Map.entry("liquidValue",
        FieldSpec.bigDecimal(
          "liquidValue",
          (root, query) -> root.get("liquidValue"))),

      Map.entry("adjustmentValue",
        FieldSpec.bigDecimal(
          "adjustmentValue",
          (root, query) -> root.join("adjustment", JoinType.LEFT).get("adjustmentValue"))),

      Map.entry("saleDate",
        FieldSpec.offsetDateTime(
          "saleDate",
          (root, query) -> root.join("transaction", JoinType.LEFT).get("saleDate"),
          dateFilterService
        )),

      // Coluna própria desta entidade (diferente de TransactionAcqTableFields, onde
      // "expectedPaymentDate" precisa de join pra "installments" — aqui já estamos no nível
      // da parcela).
      Map.entry("expectedPaymentDate",
        FieldSpec.localDate(
          "expectedPaymentDate",
          (root, query) -> root.get("expectedPaymentDate"),
          dateFilterService
        )),

      Map.entry("installment",
        FieldSpec.integer(
          "installment",
          (root, query) -> root.get("installment"))),

      // Os campos abaixo não existem em InstallmentAcqEntity — vêm da venda (transaction)
      // vinculada, mesmo path já usado em InstallmentsAcqSpecs.orderByTableSort/fetchListAssociations.
      Map.entry("cvNsu",
        FieldSpec.longNumber(
          "cvNsu",
          (root, query) -> root.join("transaction", JoinType.LEFT).get("nsu"))),

      Map.entry("authorization",
        FieldSpec.string(
          "authorization",
          (root, query) -> root.join("transaction", JoinType.LEFT).get("authorization"))),

      Map.entry("company",
        FieldSpec.joinedUuid(
          "company",
          (root, query) -> root.join("transaction", JoinType.LEFT).join("company", JoinType.LEFT).get("id")
        )),

      Map.entry("acquirer",
        FieldSpec.joinedUuid(
          "acquirer",
          (root, query) -> root.join("transaction", JoinType.LEFT).join("acquirer", JoinType.LEFT).get("id")
        )),

      Map.entry("flag",
        FieldSpec.joinedUuid(
          "flag",
          (root, query) -> root.join("transaction", JoinType.LEFT).join("flag", JoinType.LEFT).get("id")
        )),

      Map.entry("establishment",
        FieldSpec.joinedUuid(
          "establishment",
          (root, query) -> root.join("transaction", JoinType.LEFT).join("establishment", JoinType.LEFT).get("id")
        )),

      Map.entry("capture",
        FieldSpec.enumAsIntegerCode(
          "capture",
          CaptureEnum.class,
          CaptureEnum::getCode,
          (root, query) -> root.join("transaction", JoinType.LEFT).get("capture")
        )
      ),

      Map.entry("modality",
        FieldSpec.enumAsIntegerCode(
          "modality",
          ModalityEnum.class,
          ModalityEnum::getCode,
          (root, query) -> root.join("transaction", JoinType.LEFT).get("modality")
        )
      )
    );
  }
}
