package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.CompanyFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.CompanyAllowedFields;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeCompanyEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Objects;

@Component
public class CompanySpecs {

  private final DateFilterService dateFilterService;

  public CompanySpecs(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Specification<CompanyEntity> fromQuery(ListQueryDto<CompanyFilter> query) {
    Specification<CompanyEntity> spec = Specs.all();

    spec = spec.and(
      SpecificationFactory.fromTableFilters(
        query.tableFilters(),
        CompanyAllowedFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();
      spec = spec.and(textContains("cnpj", a.cnpj()));
      spec = spec.and(textContains("fantasyName", a.fantasyName()));
      spec = spec.and(textContains("socialReason", a.socialReason()));

      spec = spec.and(rangeOdt(a.createdAtFrom(), a.createdAtTo()));

      // busca aninhada
      spec = spec.and(textContainsPath(a.createdBy(), "createdBy", "name"));
      spec = spec.and(textContainsPath(a.createdBy(), "createdBy", "userName"));

      if (a.statusEnum() != null && !a.statusEnum().isEmpty()) {
        var codes = a.statusEnum().stream()
          .filter(Objects::nonNull)
          .map(StatusEnum::getCode)
          .toList();

        if (!codes.isEmpty()) {
          spec = spec.and((root, q2, cb) -> root.get("status").in(codes));
        }
      }

      if (a.typeEnum() != null && !a.typeEnum().isEmpty()) {
        var codes = a.typeEnum().stream()
          .filter(Objects::nonNull)
          .map(TypeCompanyEnum::getCode)
          .toList();

        if (!codes.isEmpty()) {
          spec = spec.and((root, q2, cb) -> root.get("type").in(codes));
        }
      }
    }

    if (query.globalFilter() != null && !query.globalFilter().trim().isEmpty()) {
      String gf = query.globalFilter().trim();

      spec = spec.and(
        textContains("cnpj", gf)
          .or(textContains("fantasyName", gf))
          .or(textContains("socialReason", gf))
          .or(textContainsPath(gf, "createdBy", "name"))
          .or(textContainsPath(gf, "createdBy", "userName"))
      );
    }
    spec = spec.and(orderBy());

    return spec;
  }

  private Specification<CompanyEntity> textContains(String field, String value) {
    if (value == null || value.trim().isEmpty()) {
      return Specs.all();
    }

    String v = value.trim().toLowerCase();

    return (root, query, cb) ->
      cb.like(cb.lower(root.get(field)), "%" + v + "%");
  }

  private Specification<CompanyEntity> textContainsPath(String value, String association, String field) {
    if (value == null || value.trim().isEmpty()) {
      return Specs.all();
    }

    String v = value.trim().toLowerCase();

    return (root, query, cb) -> {
      Expression<String> path = cb.lower(getPath(root, association, field).as(String.class));
      return cb.like(path, "%" + v + "%");
    };
  }

  private Path<?> getPath(Root<CompanyEntity> root, String association, String field) {
    From<?, ?> join = root.join(association, JoinType.LEFT);
    return join.get(field);
  }

  private Specification<CompanyEntity> orderBy() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        query.orderBy(cb.asc(root.get("fantasyName")));
      }
      return cb.conjunction();
    };
  }

  private boolean isCountQuery(CriteriaQuery<?> query) {
    return Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
  }

  private Specification<CompanyEntity> rangeOdt(String fromIso, String toIso) {
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
      var p = root.get("createdAt").as(OffsetDateTime.class);

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
