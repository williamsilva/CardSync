package com.cardsync.infrastructure.repository.spec;

import com.cardsync.core.config.CardsyncAppProperties;
import com.cardsync.domain.filter.SaleSummaryFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.SaleSummaryAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.SaleSummaryTableFields;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class SaleSummarySpecs extends BaseSpecificationSupport<SalesSummaryEntity> {

  private final CardsyncAppProperties appProperties;
  private final SpecificationFactory specificationFactory;
  private final SaleSummaryTableFields saleSummaryTableFields;
  private final SaleSummaryAdvancedFields saleSummaryAdvancedFields;

  public SaleSummarySpecs(
    DateFilterService dateFilterService,
    CardsyncAppProperties appProperties,
    SpecificationFactory specificationFactory,
    SaleSummaryTableFields saleSummaryTableFields,
    SaleSummaryAdvancedFields saleSummaryAdvancedFields
  ) {
    super(dateFilterService);
    this.appProperties = appProperties;
    this.specificationFactory = specificationFactory;
    this.saleSummaryTableFields = saleSummaryTableFields;
    this.saleSummaryAdvancedFields = saleSummaryAdvancedFields;
  }

  public Specification<SalesSummaryEntity> fromQuery(ListQueryDto<SaleSummaryFilter> query) {
    Specification<SalesSummaryEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<SalesSummaryEntity> fromQueryForTotals(ListQueryDto<SaleSummaryFilter> query) {
    return baseFilters(query);
  }

  public Specification<SalesSummaryEntity> fromQueryForPendingCreditOrders(
      ListQueryDto<SaleSummaryFilter> query, LocalDate cutoffDate) {
    Specification<SalesSummaryEntity> spec = baseFilters(query)
      .and(rvDateBefore(cutoffDate))
      .and(installmentModalitySpec())
      .and(missingCreditOrdersSpec())
      .and(fetchListAssociations());
    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<SalesSummaryEntity> fromQueryForPendingCreditOrdersTotals(
      ListQueryDto<SaleSummaryFilter> query, LocalDate cutoffDate) {
    return baseFilters(query)
      .and(rvDateBefore(cutoffDate))
      .and(installmentModalitySpec())
      .and(missingCreditOrdersSpec());
  }

  private static Specification<SalesSummaryEntity> rvDateBefore(LocalDate date) {
    return (root, query, cb) -> cb.lessThan(root.<LocalDate>get("rvDate"), date);
  }

  private static Specification<SalesSummaryEntity> installmentModalitySpec() {
    return (root, query, cb) -> root.get("modality").in(
      ModalityEnum.INSTALLMENT_CREDIT_2_6.getCode(),
      ModalityEnum.INSTALLMENT_CREDIT_7_12.getCode(),
      ModalityEnum.INSTALLMENT_CREDIT_13_21.getCode()
    );
  }

  private static Specification<SalesSummaryEntity> missingCreditOrdersSpec() {
    return (root, query, cb) -> {
      Subquery<Long> countSq = query.subquery(Long.class);
      Root<CreditOrderEntity> coRoot = countSq.from(CreditOrderEntity.class);
      countSq.select(cb.count(coRoot.get("id")))
             .where(cb.equal(coRoot.get("salesSummary"), root));

      Subquery<Long> maxSq = query.subquery(Long.class);
      Root<CreditOrderEntity> co2Root = maxSq.from(CreditOrderEntity.class);
      maxSq.select(cb.max(co2Root.<Integer>get("installmentTotal")).as(Long.class))
           .where(cb.equal(co2Root.get("salesSummary"), root));

      CriteriaBuilder.Coalesce<Long> coalesce = cb.coalesce();
      coalesce.value(maxSq);
      coalesce.value(1L);

      return cb.lt(countSq, coalesce);
    };
  }

  private Specification<SalesSummaryEntity> baseFilters(ListQueryDto<SaleSummaryFilter> query) {
    Specification<SalesSummaryEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          saleSummaryTableFields.table()
        )
      );

      spec = spec.and(saleSummaryAdvancedFields.advanced(query.advanced()));
    }

    spec = spec.and(dateGreaterThanOrEqual("rvDate", appProperties.getImplantationDate(), false));
    return spec;
  }

  private Specification<SalesSummaryEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<SalesSummaryEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "pvNumber", Map.of(
      "flag",             sortJoin("flag", "name"),
      "conciliationDate", sortField("saleReconciliationDate"),
      "company",          sortJoin("company", "fantasyName"),
      "acquirer",         sortJoin("acquirer", "fantasyName"),
      "adjustmentValue",  sortJoin("adjustment", "adjustmentValue")
    ));
  }
}
