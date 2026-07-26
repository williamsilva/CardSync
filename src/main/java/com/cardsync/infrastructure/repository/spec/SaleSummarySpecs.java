package com.cardsync.infrastructure.repository.spec;

import com.cardsync.core.config.ImplantationDateProvider;
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
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class SaleSummarySpecs extends BaseSpecificationSupport<SalesSummaryEntity> {

  private final SpecificationFactory specificationFactory;
  private final SaleSummaryTableFields saleSummaryTableFields;
  private final ImplantationDateProvider implantationDateProvider;
  private final SaleSummaryAdvancedFields saleSummaryAdvancedFields;

  public SaleSummarySpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    SaleSummaryTableFields saleSummaryTableFields,
    ImplantationDateProvider implantationDateProvider,
    SaleSummaryAdvancedFields saleSummaryAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.saleSummaryTableFields = saleSummaryTableFields;
    this.implantationDateProvider = implantationDateProvider;
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
      ListQueryDto<SaleSummaryFilter> query, LocalDate cutoffDate, LocalDate yesterday, LocalDate monthAgo) {
    Specification<SalesSummaryEntity> spec = baseFilters(query)
      .and(rvDateBefore(cutoffDate))
      .and(installmentModalitySpec())
      .and(missingCreditOrdersSpec())
      .and(nextReleaseDateNotFutureSpec(yesterday, monthAgo))
      .and(fetchListAssociations());
    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<SalesSummaryEntity> fromQueryForPendingCreditOrdersTotals(
      ListQueryDto<SaleSummaryFilter> query, LocalDate cutoffDate, LocalDate yesterday, LocalDate monthAgo) {
    return baseFilters(query)
      .and(rvDateBefore(cutoffDate))
      .and(installmentModalitySpec())
      .and(missingCreditOrdersSpec())
      .and(nextReleaseDateNotFutureSpec(yesterday, monthAgo));
  }

  private static Specification<SalesSummaryEntity> rvDateBefore(LocalDate date) {
    return (root, query, cb) -> cb.lessThan(root.<LocalDate>get("rvDate"), date);
  }

  private static Specification<SalesSummaryEntity> installmentModalitySpec() {
    return (root, query, cb) -> root.get("modality").in(
      ModalityEnum.CASH_CREDIT.getCode(),
      ModalityEnum.INSTALLMENT_CREDIT_2_6.getCode(),
      ModalityEnum.INSTALLMENT_CREDIT_7_12.getCode(),
      ModalityEnum.INSTALLMENT_CREDIT_13_21.getCode()
    );
  }

  private static Specification<SalesSummaryEntity> missingCreditOrdersSpec() {
    return (root, query, cb) -> {
      // countDistinct(installmentNumber), não count(id): CreditOrder duplicada para a mesma
      // parcela (ex.: reenvio de arquivo EEFI da adquirente cobrindo a mesma parcela já
      // importada — não há constraint único nem checagem de idempotência por parcela) não
      // pode mascarar uma parcela realmente faltante como "já coberta".
      Subquery<Long> countSq = query.subquery(Long.class);
      Root<CreditOrderEntity> coRoot = countSq.from(CreditOrderEntity.class);
      countSq.select(cb.countDistinct(coRoot.get("installmentNumber")))
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

  /**
   * Garante que a próxima parcela a ser gerada tenha vencimento <= ontem.

   * Sem ordens existentes: baseDate (parcela 1) <= yesterday.
   * Com ordens existentes: max(releaseDate) <= monthAgo, equivalente a max + 1 mês <= yesterday.
   * Isso impede gerar ordens com vencimento futuro que ainda podem vir no arquivo da adquirente.
   */
  private static Specification<SalesSummaryEntity> nextReleaseDateNotFutureSpec(
      LocalDate yesterday, LocalDate monthAgo) {
    return (root, query, cb) -> {
      // Subquery: contagem de ordens existentes
      Subquery<Long> existsCountSq = query.subquery(Long.class);
      Root<CreditOrderEntity> coEx = existsCountSq.from(CreditOrderEntity.class);
      existsCountSq.select(cb.count(coEx.get("id")))
                   .where(cb.equal(coEx.get("salesSummary"), root));

      // Subquery: maior releaseDate das ordens existentes
      Subquery<LocalDate> maxReleaseSq = query.subquery(LocalDate.class);
      Root<CreditOrderEntity> coMax = maxReleaseSq.from(CreditOrderEntity.class);
      maxReleaseSq.select(cb.greatest(coMax.<LocalDate>get("releaseDate")))
                  .where(cb.equal(coMax.get("salesSummary"), root));

      // baseDate = coalesce(firstInstallmentCreditDate, rvDate)
      CriteriaBuilder.Coalesce<LocalDate> baseDate = cb.coalesce();
      baseDate.value(root.get("firstInstallmentCreditDate"));
      baseDate.value(root.get("rvDate"));

      // Condição A: sem ordens — parcela 1 (baseDate) <= yesterday
      Predicate noOrders  = cb.equal(existsCountSq, 0L);
      Predicate baseDateOk = cb.lessThanOrEqualTo(baseDate, yesterday);

      // Condição B: com ordens — max(releaseDate) <= monthAgo ≡ próxima parcela <= yesterday
      Predicate hasOrders  = cb.greaterThan(existsCountSq, 0L);
      Predicate maxDateOk  = cb.lessThanOrEqualTo(maxReleaseSq, monthAgo);

      return cb.or(
        cb.and(noOrders,  baseDateOk),
        cb.and(hasOrders, maxDateOk)
      );
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

    spec = spec.and(dateGreaterThanOrEqual("rvDate", implantationDateProvider.get(), false));
    return spec;
  }

  private Specification<SalesSummaryEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");
        fetchIfNotFetched(root, "processedFile");

        var bankingDomicile = fetchIfNotFetched(root, "bankingDomicile");
        fetchIfNotFetched(bankingDomicile, "bank");

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
