package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.FieldSpec;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CreditOrderTableFields {

  private final DateFilterService dateFilterService;

  public CreditOrderTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<CreditOrderEntity, ?>> table() {
    return Map.ofEntries(
      // Sem FK pra EstablishmentEntity nesta entidade (ver CreditOrderAdvancedFields) — coluna
      // "pvNumber" da tela é o número cru importado do arquivo, não establishment.pvNumber.
      Map.entry("pvNumber",
        FieldSpec.integer("pvNumber", (root, query) -> root.get("originalPvNumber"))),

      Map.entry("rvNumber",
        FieldSpec.integer("rvNumber", (root, query) -> root.get("rvNumber"))),

      Map.entry("installmentNumber",
        FieldSpec.integer("installmentNumber", (root, query) -> root.get("installmentNumber"))),

      Map.entry("rvDate",
        FieldSpec.localDate("rvDate", (root, query) -> root.get("rvDate"), dateFilterService)),

      Map.entry("releaseDate",
        FieldSpec.localDate("releaseDate", (root, query) -> root.get("releaseDate"), dateFilterService)),

      Map.entry("creditOrderDate",
        FieldSpec.localDate("creditOrderDate", (root, query) -> root.get("creditOrderDate"), dateFilterService)),

      Map.entry("releaseValue",
        FieldSpec.bigDecimal("releaseValue", (root, query) -> root.get("releaseValue"))),

      Map.entry("salesSummaryStatus",
        FieldSpec.enumAsIntegerCode(
          "salesSummaryStatus", StatusReconciliationEnum.class, StatusReconciliationEnum::getCode,
          (root, query) -> root.get("salesSummaryStatus")
        )),

      Map.entry("statusPaymentBank",
        FieldSpec.enumAsIntegerCode(
          "statusPaymentBank", StatusPaymentBankEnum.class, StatusPaymentBankEnum::getCode,
          (root, query) -> root.get("statusPaymentBank")
        )),

      // Sem coluna "modality" própria — vem do resumo de vendas vinculado (ver
      // CreditOrderAdvancedFields/CreditOrderSpecs.orderByTableSort, mesmo path).
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

      // Banco vem do domicílio bancário vinculado, não é FK direta (mesmo path de
      // CreditOrderSpecs.orderByTableSort's "bank" -> sortJoin("bankingDomicile", "bank", "name")).
      Map.entry("bank",
        FieldSpec.joinedUuid("bank", (root, query) -> root.get("bankingDomicile").get("bank").get("id")))
    );
  }
}
