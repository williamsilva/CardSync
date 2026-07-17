package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.AcquirerFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.AcquirerAllowedFields;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.AcquirerAdvancedFields;
import com.cardsync.infrastructure.repository.spec.advancedFilters.TransactionAcqAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AcquirerSpecs extends BaseSpecificationSupport<AcquirerEntity> {

  private final SpecificationFactory specificationFactory;
  private final AcquirerAllowedFields acquirerAllowedFields;
  private final AcquirerAdvancedFields acquirerAdvancedFields;

  public AcquirerSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    AcquirerAllowedFields acquirerAllowedFields,
    AcquirerAdvancedFields acquirerAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.acquirerAllowedFields = acquirerAllowedFields;
    this.acquirerAdvancedFields = acquirerAdvancedFields;
  }

  public Specification<AcquirerEntity> fromQuery(ListQueryDto<AcquirerFilter> query) {
    Specification<AcquirerEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(specificationFactory.fromTableFilters(query.tableFilters(), acquirerAllowedFields.table()));

      spec = spec.and(acquirerAdvancedFields.advanced(query.advanced()));
    }

    spec = spec.and(fetchListAssociations());

    return spec.and(orderByAsc("fantasyName"));
  }

  /**
   * initializeRelations() (AcquirerService) toca acquirerCompanies+company e
   * acquirerEstablishments+establishment+company para cada linha da página — sem fetch,
   * isso era N+1 puro. Só dá pra fetch-joinar UMA das duas coleções (acquirerCompanies e
   * acquirerEstablishments são ambas List/bag — join fetch nas duas ao mesmo tempo dispara
   * MultipleBagFetchException do Hibernate). A outra fica coberta por @BatchSize(100) na
   * entidade, que agrupa o lazy-load em poucas queries em vez de uma por linha.
   */
  private Specification<AcquirerEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        var acquirerCompanies = fetchIfNotFetched(root, "acquirerCompanies");
        fetchIfNotFetched(acquirerCompanies, "company");

        query.distinct(true);
      }

      return cb.conjunction();
    };
  }
}