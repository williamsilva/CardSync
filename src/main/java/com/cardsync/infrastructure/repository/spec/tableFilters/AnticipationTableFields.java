package com.cardsync.infrastructure.repository.spec.tableFilters;

import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.nimbussystems.commons.legacy.filter.spec.DateFilterService;
import com.nimbussystems.commons.legacy.filter.spec.FieldSpec;
import jakarta.persistence.criteria.JoinType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AnticipationTableFields {

  private final DateFilterService dateFilterService;

  public AnticipationTableFields(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Map<String, FieldSpec<AnticipationEntity, ?>> table() {
    return Map.ofEntries(
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

      // "bank" na tela é o banco cadastrado (multiselect por id) — a coluna própria
      // AnticipationEntity.bank é só o texto cru importado do arquivo, sem FK; o vínculo real é
      // via domicílio bancário (mesmo path usado em CreditOrder/SaleSummary pra este mesmo caso).
      Map.entry("bank",
        FieldSpec.joinedUuid(
          "bank",
          (root, query) -> root.join("bankingDomicile", JoinType.LEFT).join("bank", JoinType.LEFT).get("id")
        )),

      Map.entry("originalDueDate",
        FieldSpec.localDate("originalDueDate", (root, query) -> root.get("originalDueDate"), dateFilterService)),

      Map.entry("releaseDate",
        FieldSpec.localDate("releaseDate", (root, query) -> root.get("releaseDate"), dateFilterService)),

      Map.entry("numberRvCorresponding",
        FieldSpec.integer("numberRvCorresponding", (root, query) -> root.get("numberRvCorresponding"))),

      Map.entry("installmentNumber",
        FieldSpec.integer("installmentNumber", (root, query) -> root.get("installmentNumber"))),

      Map.entry("grossValue",
        FieldSpec.bigDecimal("grossValue", (root, query) -> root.get("grossValue"))),

      Map.entry("releaseValue",
        FieldSpec.bigDecimal("releaseValue", (root, query) -> root.get("releaseValue"))),

      Map.entry("discountRateValue",
        FieldSpec.bigDecimal("discountRateValue", (root, query) -> root.get("discountRateValue"))),

      Map.entry("originalCreditValue",
        FieldSpec.bigDecimal("originalCreditValue", (root, query) -> root.get("originalCreditValue"))),

      // "numberCvNsu"/"transactionsStatus"/"statusPaymentBank" não são colunas próprias — vêm do
      // resumo de vendas vinculado (mesmo path de AnticipationAdvancedFields e do que a tela
      // realmente exibe: row.salesSummary?.numberCvNsu, ver anticipation-list.component.html).
      // reuseOrJoin (não root.join direto) — achado real 2026-09-11: "salesSummary" é a mesma
      // associação trazida via fetch em AnticipationSpecs#fetchListAssociations(); um join solto
      // aqui abria um SEGUNDO alias pra cs_sales_summary, e ordenar por ele (ver
      // AnticipationSpecs#orderByTableSort) quebrava com "para SELECT DISTINCT, expressões ORDER
      // BY devem aparecer na lista de seleção" porque esse alias nunca aparecia no SELECT (só o
      // do fetch aparece). reuseOrJoin reaproveita o fetch já aberto (AnticipationSpecs aplica o
      // fetch ANTES dos filtros, exatamente para isto funcionar) em vez de abrir um novo.
      Map.entry("numberCvNsu",
        FieldSpec.integer(
          "numberCvNsu",
          (root, query) -> BaseSpecificationSupport.reuseOrJoin(root, "salesSummary").get("numberCvNsu")
        )),

      Map.entry("transactionsStatus",
        FieldSpec.enumAsIntegerCode(
          "transactionsStatus", StatusReconciliationEnum.class, StatusReconciliationEnum::getCode,
          (root, query) -> BaseSpecificationSupport.reuseOrJoin(root, "salesSummary").get("transactionsStatus")
        )),

      Map.entry("statusPaymentBank",
        FieldSpec.enumAsIntegerCode(
          "statusPaymentBank", StatusPaymentBankEnum.class, StatusPaymentBankEnum::getCode,
          (root, query) -> BaseSpecificationSupport.reuseOrJoin(root, "salesSummary").get("statusPaymentBank")
        ))

      // "advanceDiscountValue" (coluna + filtro existem no frontend) foi deixado de fora: não
      // existe em AnticipationEntity nem em nenhuma associação, e o modelo de API também nunca
      // devolve esse campo (AnticipationModelAssembler não seta advanceDiscountValue) — a coluna
      // sempre mostrou vazio em produção. É uma lacuna de feature/dado, não um mismatch de chave;
      // precisa de decisão de produto (de onde esse valor deveria vir) antes de implementar.
    );
  }
}
