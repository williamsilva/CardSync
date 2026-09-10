package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.AnticipationFilter;
import com.nimbussystems.commons.legacy.filter.query.ListQueryDto;
import com.nimbussystems.commons.legacy.filter.query.SortDto;
import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.infrastructure.repository.spec.advancedFilters.AnticipationAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.nimbussystems.commons.legacy.filter.spec.DateFilterService;
import com.nimbussystems.commons.legacy.filter.spec.SpecificationFactory;
import com.nimbussystems.commons.legacy.filter.spec.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.AnticipationTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AnticipationSpecs extends BaseSpecificationSupport<AnticipationEntity> {

  private final SpecificationFactory specificationFactory;
  private final AnticipationTableFields anticipationTableFields;
  private final AnticipationAdvancedFields anticipationAdvancedFields;

  public AnticipationSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    AnticipationTableFields anticipationTableFields,
    AnticipationAdvancedFields anticipationAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.anticipationTableFields = anticipationTableFields;
    this.anticipationAdvancedFields = anticipationAdvancedFields;
  }

  public Specification<AnticipationEntity> fromQuery(ListQueryDto<AnticipationFilter> query) {
    Specification<AnticipationEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<AnticipationEntity> fromQueryForTotals(ListQueryDto<AnticipationFilter> query) {
    return baseFilters(query);
  }

  private Specification<AnticipationEntity> baseFilters(ListQueryDto<AnticipationFilter> query) {
    Specification<AnticipationEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          anticipationTableFields.table()
        )
      );

      spec = spec.and(anticipationAdvancedFields.advanced(query.advanced()));
    }

    return spec;
  }

  private Specification<AnticipationEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");
        fetchIfNotFetched(root, "establishment");
        fetchIfNotFetched(root, "processedFile");
        fetchIfNotFetched(root, "salesSummary");

        var bankingDomicile = fetchIfNotFetched(root, "bankingDomicile");
        fetchIfNotFetched(bankingDomicile, "bank");

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<AnticipationEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "pvNumber", Map.ofEntries(
      Map.entry("conciliationDate",  sortField("saleReconciliationDate")),
      Map.entry("company",           sortJoin("company", "fantasyName")),
      Map.entry("establishment",     sortJoin("establishment", "pvNumber")),
      Map.entry("acquirer",          sortJoin("acquirer", "fantasyName")),
      Map.entry("flag",              sortJoin("flag", "name")),
      Map.entry("adjustmentValue",   sortJoin("adjustment", "adjustmentValue")),

      // Mesmas 4 colunas que só existem via associação em AnticipationTableFields (filtro) —
      // sem alias aqui, o sort cai no fallback direto no root (directRootPathOrNull): silencioso
      // (sem ordenar) pras 3 de salesSummary, ou ordena pelo texto cru errado no caso de "bank"
      // (AnticipationEntity.bank é só o texto importado, sem FK — ver comentário em
      // AnticipationTableFields). Achado real 2026-09-10: sort por "transactionsStatus" batia
      // direto em AnticipationEntity via Sort.by() do Spring Data (não pelo tableSort aqui, que
      // já é seguro) e quebrava com "No property 'transactionsStatus' found" — ver o fix em
      // AnticipationService#search (pageableWithoutSort).
      Map.entry("bank",               sortJoin("bankingDomicile", "bank", "name")),
      Map.entry("numberCvNsu",        sortJoin("salesSummary", "numberCvNsu")),
      Map.entry("transactionsStatus", sortJoin("salesSummary", "transactionsStatus")),
      Map.entry("statusPaymentBank",  sortJoin("salesSummary", "statusPaymentBank"))
    ));
  }
}