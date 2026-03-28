package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.UsersFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.UserAllowedFields;
import com.cardsync.domain.model.GroupEntity;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.model.enums.StatusUserEnum;
import java.time.OffsetDateTime;
import java.util.Objects;

import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import jakarta.persistence.criteria.CriteriaQuery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class UserSpecs {

  private final DateFilterService dateFilterService;

  public UserSpecs(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Specification<UserEntity> fromQuery(ListQueryDto<UsersFilter> query) {
    Specification<UserEntity> spec = Specs.all();

    spec = spec.and(
      SpecificationFactory.fromTableFilters(
        query.tableFilters(),
        UserAllowedFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();

      spec = spec.and(textContains("name", a.name()));
      spec = spec.and(textContains("userName", a.userName()));
      spec = spec.and(textContains("document", a.document()));

      if (a.status() != null && !a.status().isEmpty()) {
        var codes = a.status().stream()
          .filter(Objects::nonNull)
          .map(StatusUserEnum::getCode)
          .toList();

        if (!codes.isEmpty()) {
          spec = spec.and((root, q2, cb) -> root.get("status").in(codes));
        }
      }

      spec = spec.and(rangeOdt("createdAt", a.createdAtFrom(), a.createdAtTo()));
      spec = spec.and(rangeOdt("lastLoginAt", a.lastLoginAtFrom(), a.lastLoginAtTo()));
      spec = spec.and(rangeOdt("blockedUntil", a.blockedUntilFrom(), a.blockedUntilTo()));
      spec = spec.and(rangeOdt("passwordExpiresAt", a.passwordExpiresAtFrom(), a.passwordExpiresAtTo()));
    }

    if (query.globalFilter() != null && !query.globalFilter().trim().isEmpty()) {
      String gf = query.globalFilter().trim();

      spec = spec.and(
        textContains("name", gf)
          .or(textContains("userName", gf))
          .or(textContains("document", gf))
      );
    }
    spec = spec.and(orderByName());
    return spec;
  }

  private Specification<UserEntity> orderByName() {
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

  private Specification<UserEntity> textContains(String field, String value) {
    if (value == null || value.trim().isEmpty()) {
      return Specs.all();
    }

    String v = value.trim().toLowerCase();

    return (root, query, cb) ->
      cb.like(cb.lower(root.get(field)), "%" + v + "%");
  }

  private Specification<UserEntity> rangeOdt(String field, String fromIso, String toIso) {
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
