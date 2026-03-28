package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.GroupsFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.GroupAllowedFields;
import com.cardsync.domain.model.GroupEntity;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import jakarta.persistence.criteria.CriteriaQuery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class GroupSpecs {

  private static final String EXCLUDED_GROUP_NAME = "SUPPORT";

  private final DateFilterService dateFilterService;

  public GroupSpecs(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Specification<GroupEntity> fromQuery(ListQueryDto<GroupsFilter> query) {
    Specification<GroupEntity> spec = Specs.all();

    spec = spec.and(excludeSupportGroup());

    spec = spec.and(
      SpecificationFactory.fromTableFilters(
        query.tableFilters(),
        GroupAllowedFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();

      spec = spec.and(textContains("name", a.name()));
      spec = spec.and(textContains("description", a.description()));

      spec = spec.and(rangeOdt("createdAt", a.createdAtFrom(), a.createdAtTo()));
    }

    if (query.globalFilter() != null && !query.globalFilter().trim().isEmpty()) {
      String gf = query.globalFilter().trim();

      spec = spec.and(
        textContains("name", gf)
          .or(textContains("description", gf))
      );
    }
    spec = spec.and(orderByName());
    return spec;
  }

  private Specification<GroupEntity> orderByName() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        query.orderBy(cb.asc(root.get("name")));
      }
      return cb.conjunction();
    };
  }

  private boolean isCountQuery(CriteriaQuery<?> query) {
    return Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
  }

  private Specification<GroupEntity> excludeSupportGroup() {
    return (root, query, cb) ->
      cb.notEqual(cb.lower(root.get("name")), EXCLUDED_GROUP_NAME.toLowerCase());
  }

  private Specification<GroupEntity> textContains(String field, String value) {
    if (value == null || value.trim().isEmpty()) {
      return Specs.all();
    }

    String v = value.trim().toLowerCase();

    return (root, query, cb) ->
      cb.like(cb.lower(root.get(field)), "%" + v + "%");
  }

  private Specification<GroupEntity> rangeOdt(String field, String fromIso, String toIso) {
    OffsetDateTime from = (fromIso == null || fromIso.isBlank())
      ? null
      : dateFilterService.startOfBusinessDay(fromIso);

    OffsetDateTime to = (toIso == null || toIso.isBlank())
      ? null
      : dateFilterService.endOfBusinessDay(toIso);

    if (from == null && to == null) {
      return Specs.all();
    }

    return (root, query, cb) -> {
      var p = root.get(field).as(OffsetDateTime.class);

      if (from != null && to != null) {
        return cb.between(p, from, to);
      }

      if (from != null) {
        return cb.greaterThanOrEqualTo(p, from);
      }

      return cb.lessThanOrEqualTo(p, to);
    };
  }
}
