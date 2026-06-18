package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.ReleasesBankFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.ReleasesBankAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.ReleasesBankTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ReleasesBankSpecs extends BaseSpecificationSupport<ReleasesBankEntity> {

  private final SpecificationFactory specificationFactory;
  private final ReleasesBankTableFields releasesBankTableFields;
  private final ReleasesBankAdvancedFields releasesBankAdvancedFields;

  public ReleasesBankSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    ReleasesBankTableFields releasesBankTableFields,
    ReleasesBankAdvancedFields releasesBankAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.releasesBankTableFields = releasesBankTableFields;
    this.releasesBankAdvancedFields = releasesBankAdvancedFields;
  }

  public Specification<ReleasesBankEntity> fromQuery(ListQueryDto<ReleasesBankFilter> query) {
    Specification<ReleasesBankEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<ReleasesBankEntity> fromQueryForTotals(ListQueryDto<ReleasesBankFilter> query) {
    return baseFilters(query);
  }

  private Specification<ReleasesBankEntity> baseFilters(ListQueryDto<ReleasesBankFilter> query) {
    Specification<ReleasesBankEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          releasesBankTableFields.table()
        )
      );

      spec = spec.and(releasesBankAdvancedFields.advanced(query.advanced()));
      spec = spec.and(inCodes("modalityPaymentBank", getAdjustmentCodes(), ModalityPaymentBankEnum::getCode)
      );
    }

    return spec;
  }

  private Specification<ReleasesBankEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        // distinct apenas na query de dados
        query.distinct(true);
      }
      return cb.conjunction();
    };
  }

  private Specification<ReleasesBankEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "releaseDate", Map.of(
      "flag",             sortJoin("flag", "name"),
      "company",          sortJoin("company", "fantasyName"),
      "acquirer",         sortJoin("acquirer", "fantasyName")
    ));
  }

  private static List<ModalityPaymentBankEnum> getAdjustmentCodes() {
    return List.of(
      ModalityPaymentBankEnum.CASH_DEBIT,
      ModalityPaymentBankEnum.CASH_CREDIT,
      ModalityPaymentBankEnum.ANTECIP_CRED
      //ModalityPaymentBankEnum.PIX_REC
    );
  }
}