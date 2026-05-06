package com.cardsync.infrastructure.repository.spec.config;

import com.cardsync.domain.model.enums.PeriodEnum;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.function.BiFunction;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public abstract class BaseSpecificationSupport<T> {

  private static final DateTimeFormatter BR_MONTH = DateTimeFormatter.ofPattern("MM/yyyy");
  private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final DateFilterService dateFilterService;

  protected BaseSpecificationSupport(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  protected Specification<T> alwaysTrue() {
    return Specs.all();
  }

  protected Specification<T> contains(String value, String field) {
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

  protected Specification<T> datePeriod(
    String field, PeriodEnum period, List<String> values, boolean nullableField) {
    if (period == null || period == PeriodEnum.NULL || values == null || values.isEmpty()) {
      return alwaysTrue();
    }

    return switch (period) {
      case DAY -> {
        LocalDate date = firstDate(values);
        yield date == null ? alwaysTrue() : dateEquals(field, date, nullableField);
      }

      case START -> {
        LocalDate date = firstDate(values);
        yield date == null ? alwaysTrue() : dateGreaterThanOrEqual(field, date, nullableField);
      }

      case END -> {
        LocalDate date = firstDate(values);
        yield date == null ? alwaysTrue() : dateLessThanOrEqual(field, date, nullableField);
      }

      case MONTH -> {
        YearMonth month = parseMonth(firstText(values));
        if (month == null) {
          yield alwaysTrue();
        }
        yield dateBetween(field, month.atDay(1), month.atEndOfMonth(), nullableField);
      }

      case YEAR -> {
        Year year = parseYear(firstText(values));
        if (year == null) {
          yield alwaysTrue();
        }
        yield dateBetween(field, year.atDay(1), year.atMonth(12).atEndOfMonth(), nullableField);
      }

      case INTERVAL -> {
        DateRange range = parseInterval(values);
        yield range == null ? alwaysTrue() : dateBetween(field, range.start(), range.end(), nullableField);
      }

      case NULL -> alwaysTrue();
    };
  }

  protected Specification<T> datePeriodJoin(
    String joinField, String dateField, PeriodEnum period, List<String> values, boolean nullableField) {
    if (period == null || period == PeriodEnum.NULL || values == null || values.isEmpty()) {
      return alwaysTrue();
    }

    return switch (period) {
      case DAY -> {
        LocalDate date = firstDate(values);

        yield date == null
          ? alwaysTrue()
          : dateJoinEquals(joinField, dateField, date, nullableField);
      }

      case START -> {
        LocalDate date = firstDate(values);

        yield date == null
          ? alwaysTrue()
          : dateJoinGreaterThanOrEqual(joinField, dateField, date, nullableField);
      }

      case END -> {
        LocalDate date = firstDate(values);

        yield date == null
          ? alwaysTrue()
          : dateJoinLessThanOrEqual(joinField, dateField, date, nullableField);
      }

      case MONTH -> {
        YearMonth month = parseMonth(firstText(values));

        yield month == null
          ? alwaysTrue()
          : dateJoinBetween(joinField, dateField, month.atDay(1), month.atEndOfMonth(), nullableField);
      }

      case YEAR -> {
        Year year = parseYear(firstText(values));

        yield year == null
          ? alwaysTrue()
          : dateJoinBetween(joinField, dateField, year.atDay(1), year.atMonth(12).atEndOfMonth(), nullableField);
      }

      case INTERVAL -> {
        DateRange range = parseInterval(values);

        yield range == null
          ? alwaysTrue()
          : dateJoinBetween(joinField, dateField, range.start(), range.end(), nullableField);
      }

      case NULL -> alwaysTrue();
    };
  }

  protected Specification<T> dateEquals(String field, LocalDate value, boolean nullableField) {
    return (root, query, cb) -> {
      var path = root.<LocalDate>get(field);

      if (!nullableField) {
        return cb.equal(path, value);
      }

      return cb.and(
        cb.isNotNull(path),
        cb.equal(path, value)
      );
    };
  }

  protected Specification<T> dateGreaterThanOrEqual(String field, LocalDate value, boolean nullableField) {
    return (root, query, cb) -> {
      var path = root.<LocalDate>get(field);

      if (!nullableField) {
        return cb.greaterThanOrEqualTo(path, value);
      }

      return cb.and(
        cb.isNotNull(path),
        cb.greaterThanOrEqualTo(path, value)
      );
    };
  }

  protected Specification<T> dateLessThanOrEqual(String field, LocalDate value, boolean nullableField) {
    return (root, query, cb) -> {
      var path = root.<LocalDate>get(field);

      if (!nullableField) {
        return cb.lessThanOrEqualTo(path, value);
      }

      return cb.and(
        cb.isNotNull(path),
        cb.lessThanOrEqualTo(path, value)
      );
    };
  }

  protected Specification<T> dateBetween(String field, LocalDate start, LocalDate end, boolean nullableField) {
    return (root, query, cb) -> {
      var path = root.<LocalDate>get(field);

      if (!nullableField) {
        return cb.and(
          cb.greaterThanOrEqualTo(path, start),
          cb.lessThanOrEqualTo(path, end)
        );
      }

      return cb.and(
        cb.isNotNull(path),
        cb.greaterThanOrEqualTo(path, start),
        cb.lessThanOrEqualTo(path, end)
      );
    };
  }

  protected LocalDate firstDate(List<String> values) {
    return parseDate(firstText(values));
  }

  protected String firstText(List<String> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    return normalizeDate(values.getFirst());
  }

  protected DateRange parseInterval(List<String> values) {
    if (values == null || values.size() < 2) {
      return null;
    }

    LocalDate start = parseDate(values.get(0));
    LocalDate end = parseDate(values.get(1));

    if (start == null || end == null) {
      return null;
    }

    if (end.isBefore(start)) {
      LocalDate tmp = start;
      start = end;
      end = tmp;
    }

    return new DateRange(start, end);
  }

  protected YearMonth parseMonth(String raw) {
    String value = normalizeDate(raw);
    if (value == null) {
      return null;
    }

    try {
      return YearMonth.parse(value, BR_MONTH);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  protected Year parseYear(String raw) {
    String value = normalizeDate(raw);
    if (value == null) {
      return null;
    }

    try {
      return Year.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  protected LocalDate parseDate(String raw) {
    String value = normalizeDate(raw);
    if (value == null) {
      return null;
    }

    try {
      return LocalDate.parse(value, BR_DATE);
    } catch (DateTimeParseException ignored) {
    }

    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  protected String normalizeDate(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
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

  protected static void fetchIfNotFetched(Root<?> root, String attributeName) {
    boolean alreadyFetched = root.getFetches()
      .stream()
      .map(Fetch::getAttribute)
      .anyMatch(attribute -> attributeName.equals(attribute.getName()));

    if (!alreadyFetched) {
      root.fetch(attributeName, JoinType.LEFT);
    }
  }

  protected static UUID parseUuidOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      return null;
    }
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

  private Specification<T> dateJoinEquals(
    String joinField, String dateField, LocalDate value, boolean nullableField) {
    return dateJoinPredicate(joinField, dateField, nullableField, (cb, path) ->
      cb.equal(path, value)
    );
  }

  private Specification<T> dateJoinGreaterThanOrEqual(
    String joinField, String dateField, LocalDate value, boolean nullableField) {
    return dateJoinPredicate(joinField, dateField, nullableField, (cb, path) ->
      cb.greaterThanOrEqualTo(path, value)
    );
  }

  private Specification<T> dateJoinLessThanOrEqual(
    String joinField, String dateField, LocalDate value, boolean nullableField) {
    return dateJoinPredicate(joinField, dateField, nullableField, (cb, path) ->
      cb.lessThanOrEqualTo(path, value)
    );
  }

  private Specification<T> dateJoinBetween(
    String joinField, String dateField, LocalDate start, LocalDate end, boolean nullableField) {
    return dateJoinPredicate(joinField, dateField, nullableField, (cb, path) ->
      cb.and(
        cb.greaterThanOrEqualTo(path, start),
        cb.lessThanOrEqualTo(path, end)
      )
    );
  }

  private Specification<T> dateJoinPredicate(String joinField, String dateField, boolean nullableField,
    BiFunction<CriteriaBuilder, Path<LocalDate>, Predicate> predicateFactory) {
    return (root, query, cb) -> {
      query.distinct(true);

      Join<?, ?> join = root.join(joinField, JoinType.LEFT);
      Path<LocalDate> path = join.get(dateField);

      Predicate predicate = predicateFactory.apply(cb, path);

      if (!nullableField) {
        return predicate;
      }

      return cb.and(
        cb.isNotNull(path),
        predicate
      );
    };
  }

  protected record DateRange(LocalDate start, LocalDate end) {
  }
}