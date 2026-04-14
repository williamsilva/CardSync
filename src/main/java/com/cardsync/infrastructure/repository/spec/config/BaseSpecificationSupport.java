package com.cardsync.infrastructure.repository.spec.config;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

public abstract class BaseSpecificationSupport<T> {

  private final DateFilterService dateFilterService;

  protected BaseSpecificationSupport(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  protected Specification<T> alwaysTrue() {
    return Specs.all();
  }

  protected Specification<T> contains(String field, String value) {
    if (isBlank(value)) {
      return alwaysTrue();
    }

    String normalized = normalize(value);

    return (root, query, cb) ->
      cb.like(cb.lower(root.get(field).as(String.class)), like(normalized));
  }

  protected <V> Specification<T> equalsTo(String field, V value) {
    if (value == null) {
      return alwaysTrue();
    }

    return (root, query, cb) -> cb.equal(root.get(field), value);
  }

  protected Specification<T> containsPath(String value, String association, String field) {
    if (isBlank(value)) {
      return alwaysTrue();
    }

    String normalized = normalize(value);

    return (root, query, cb) -> {
      Expression<String> path = cb.lower(root.join(association, JoinType.LEFT).get(field).as(String.class));
      return cb.like(path, like(normalized));
    };
  }

  protected Specification<T> containsPath(String value, String... path) {
    if (isBlank(value)) {
      return alwaysTrue();
    }

    String normalized = normalize(value);

    return (root, query, cb) -> {
      From<?, ?> join = null;

      for (int i = 0; i < path.length - 1; i++) {
        join = (join == null)
          ? root.join(path[i], JoinType.LEFT)
          : join.join(path[i], JoinType.LEFT);
      }

      Path<?> leaf = (join == null)
        ? root.get(path[path.length - 1])
        : join.get(path[path.length - 1]);

      return cb.like(cb.lower(leaf.as(String.class)), like(normalized));
    };
  }

  protected <V> Specification<T> equalsPath(V value, String... path) {
    if (value == null) {
      return alwaysTrue();
    }

    return (root, query, cb) -> {
      From<?, ?> join = null;

      for (int i = 0; i < path.length - 1; i++) {
        join = (join == null)
          ? root.join(path[i], JoinType.LEFT)
          : join.join(path[i], JoinType.LEFT);
      }

      Path<?> leaf = (join == null)
        ? root.get(path[path.length - 1])
        : join.get(path[path.length - 1]);

      return cb.equal(leaf, value);
    };
  }

  protected <E> Specification<T> inCodes(String field, Collection<E> values, Function<E, ?> mapper) {
    if (values == null || values.isEmpty()) {
      return alwaysTrue();
    }

    var mapped = values.stream()
      .filter(Objects::nonNull)
      .map(mapper)
      .filter(Objects::nonNull)
      .toList();

    if (mapped.isEmpty()) {
      return alwaysTrue();
    }

    return (root, query, cb) -> root.get(field).in(mapped);
  }

  protected <E extends Enum<E>> Specification<T> inEnums(String field, Collection<E> values) {
    if (values == null || values.isEmpty()) {
      return alwaysTrue();
    }

    var mapped = values.stream()
      .filter(Objects::nonNull)
      .toList();

    if (mapped.isEmpty()) {
      return alwaysTrue();
    }

    return (root, query, cb) -> root.get(field).in(mapped);
  }

  protected <V> Specification<T> inPath(Collection<V> values, Function<V, ?> mapper, String... path) {
    if (values == null || values.isEmpty()) {
      return alwaysTrue();
    }

    var mapped = values.stream()
      .filter(Objects::nonNull)
      .map(mapper)
      .filter(Objects::nonNull)
      .toList();

    if (mapped.isEmpty()) {
      return alwaysTrue();
    }

    return (root, query, cb) -> {
      From<?, ?> join = null;

      for (int i = 0; i < path.length - 1; i++) {
        join = (join == null)
          ? root.join(path[i], JoinType.LEFT)
          : join.join(path[i], JoinType.LEFT);
      }

      Path<?> leaf = (join == null)
        ? root.get(path[path.length - 1])
        : join.get(path[path.length - 1]);

      return leaf.in(mapped);
    };
  }

  protected Specification<T> rangeOdt(String field, String fromIso, String toIso) {
    OffsetDateTime fromValue = isBlank(fromIso)
      ? null
      : dateFilterService.parseFlexibleToOffsetDateTime(fromIso);

    OffsetDateTime toValue = isBlank(toIso)
      ? null
      : dateFilterService.parseFlexibleToOffsetDateTime(toIso);

    OffsetDateTime from = fromValue == null
      ? null
      : dateFilterService.startOfBusinessDay(fromValue);

    OffsetDateTime to = toValue == null
      ? null
      : dateFilterService.endOfBusinessDay(toValue);

    if (from == null && to == null) {
      return alwaysTrue();
    }

    return (root, query, cb) -> {
      var path = root.get(field).as(OffsetDateTime.class);

      if (from != null && to != null) {
        return cb.between(path, from, to);
      }
      if (from != null) {
        return cb.greaterThanOrEqualTo(path, from);
      }
      return cb.lessThanOrEqualTo(path, to);
    };
  }

  protected Specification<T> orderByAsc(String field) {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        query.orderBy(cb.asc(root.get(field)));
      }
      return cb.conjunction();
    };
  }

  protected Specification<T> orderByDesc(String field) {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        query.orderBy(cb.desc(root.get(field)));
      }
      return cb.conjunction();
    };
  }

  protected Specification<T> orderByAscPath(String... path) {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        From<?, ?> join = null;

        for (int i = 0; i < path.length - 1; i++) {
          join = (join == null)
            ? root.join(path[i], JoinType.LEFT)
            : join.join(path[i], JoinType.LEFT);
        }

        Path<?> leaf = (join == null)
          ? root.get(path[path.length - 1])
          : join.get(path[path.length - 1]);

        query.orderBy(cb.asc(leaf));
      }

      return cb.conjunction();
    };
  }

  protected Specification<T> anyOf(Specification<T>... specs) {
    Specification<T> result = null;

    for (Specification<T> spec : specs) {
      if (spec == null) {
        continue;
      }
      result = (result == null) ? Specification.where(spec) : result.or(spec);
    }

    return result == null ? alwaysTrue() : result;
  }

  protected boolean isCountQuery(CriteriaQuery<?> query) {
    return Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
  }

  protected boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  protected String normalize(String value) {
    return value.trim().toLowerCase();
  }

  protected String like(String value) {
    return "%" + value + "%";
  }
}