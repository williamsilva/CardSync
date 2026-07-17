package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.EstablishmentFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.infrastructure.repository.spec.advancedFilters.EstablishmentAdvancedFields;
import com.cardsync.infrastructure.repository.spec.tableFilters.EstablishmentTableFields;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EstablishmentSpecs extends BaseSpecificationSupport<EstablishmentEntity> {

  private final SpecificationFactory specificationFactory;
  private final EstablishmentTableFields establishmentAllowedFields;
  private final EstablishmentAdvancedFields establishmentAdvancedFields;

  public EstablishmentSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    EstablishmentTableFields establishmentAllowedFields,
    EstablishmentAdvancedFields establishmentAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.establishmentAllowedFields = establishmentAllowedFields;
    this.establishmentAdvancedFields = establishmentAdvancedFields;
  }

  public Specification<EstablishmentEntity> fromQuery(ListQueryDto<EstablishmentFilter> query) {
    Specification<EstablishmentEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<EstablishmentEntity> baseFilters(ListQueryDto<EstablishmentFilter> query) {
    Specification<EstablishmentEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(specificationFactory.fromTableFilters(query.tableFilters(), establishmentAllowedFields.table()));

      spec = spec.and(establishmentAdvancedFields.advanced(query.advanced()));
    }

    return spec;
  }

  private Specification<EstablishmentEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        // createdBy não é mais associação (é UUID puro pós-split), nada a fazer fetch
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");

        // distinct apenas na query de dados
        query.distinct(true);
      }

      return cb.conjunction();
    };
  }

  private Specification<EstablishmentEntity> orderByTableSort(List<SortDto> sort) {
    boolean hasSort = sort != null && !sort.isEmpty();

    if (!hasSort) {
      return (root, query, cb) -> {
        if (isCountQuery(query)) {
          return cb.conjunction();
        }

        // Reaproveita o fetch join de "company" (não criar um join novo: com DISTINCT,
        // o Postgres exige que a coluna do ORDER BY esteja entre as colunas selecionadas).
        Path<?> companyPath = (Path<?>) fetchIfNotFetched(root, "company");
        Expression<?> companyName = companyPath.get("fantasyName");
        query.orderBy(List.of(cb.asc(companyName), cb.desc(root.get("id"))));
        query.distinct(true);

        return cb.conjunction();
      };
    }

    return tableSort(sort, null, Map.of(
      "createdBy",            sortField("createdBy"),
      "company",             sortJoin("company", "fantasyName"),
      "acquirer",            sortJoin("acquirer", "fantasyName")
    ));
  }
}