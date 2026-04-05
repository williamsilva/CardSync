package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.GroupsFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.GroupAllowedFields;
import com.cardsync.domain.model.GroupEntity;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GroupSpecs extends BaseSpecificationSupport<GroupEntity> {

  private static final String EXCLUDED_GROUP_NAME = "SUPPORT";

  private final GroupAllowedFields groupAllowedFields;
  private final SpecificationFactory specificationFactory;

  public GroupSpecs(
    DateFilterService dateFilterService,
    GroupAllowedFields groupAllowedFields,
    SpecificationFactory specificationFactory
    ) {
    super(dateFilterService);
    this.groupAllowedFields = groupAllowedFields;
    this.specificationFactory = specificationFactory;
  }

  public Specification<GroupEntity> fromQuery(ListQueryDto<GroupsFilter> query) {
    Specification<GroupEntity> spec = Specs.all();

    spec = spec.and(excludeSupportGroup());

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        groupAllowedFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();

      spec = spec.and(contains("name", a.name()));
      spec = spec.and(contains("description", a.description()));
      spec = spec.and(rangeOdt("createdAt", a.createdAtFrom(), a.createdAtTo()));

      spec = spec.and(
        inPath(a.createdBy(), value -> {
          try {
            return UUID.fromString(value);
          } catch (Exception e) {
            return null;
          }
        }, "createdBy", "id")
      );
    }

    if (!isBlank(query.globalFilter())) {
      String gf = query.globalFilter();

      spec = spec.and(
        anyOf(
          contains("name", gf)
        )
      );
    }
    spec = spec.and(orderByAsc("name"));
    return spec;
  }

  private Specification<GroupEntity> excludeSupportGroup() {
    return (root, query, cb) ->
      cb.notEqual(cb.lower(root.get("name")), EXCLUDED_GROUP_NAME.toLowerCase());
  }
}
