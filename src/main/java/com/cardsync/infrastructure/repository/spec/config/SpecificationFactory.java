package com.cardsync.infrastructure.repository.spec.config;

import com.cardsync.domain.filter.query.ColumnFilterDto;
import com.cardsync.domain.filter.query.FilterRuleDto;
import com.cardsync.domain.filter.query.RangeValue;
import com.cardsync.domain.model.enums.PeriodEnum;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SpecificationFactory {

  private final DateFilterService dateFilterService;

  public SpecificationFactory(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public <T> Specification<T> fromTableFilters(
    Map<String, ColumnFilterDto> tableFilters,
    Map<String, FieldSpec<T, ?>> allowedFields
  ) {
    if (tableFilters == null || tableFilters.isEmpty()) {
      return Specs.all();
    }

    Specification<T> spec = Specs.all();

    for (var entry : tableFilters.entrySet()) {
      String fieldName = entry.getKey();
      ColumnFilterDto columnFilter = entry.getValue();

      FieldSpec<T, ?> fieldSpec = allowedFields.get(fieldName);

      if (fieldSpec == null) {
        log.debug("Ignoring unknown table filter field: {}", fieldName);
        continue;
      }

      spec = spec.and(applyColumnFilter(fieldSpec, columnFilter));
    }

    return spec;
  }

  private <T> Specification<T> applyColumnFilter(
    FieldSpec<T, ?> field,
    ColumnFilterDto columnFilter
  ) {
    if (
      columnFilter == null ||
        columnFilter.constraints() == null ||
        columnFilter.constraints().isEmpty()
    ) {
      return Specs.all();
    }

    String operator = columnFilter.operator() == null
      ? "and"
      : columnFilter.operator().trim().toLowerCase();

    Specification<T> acc = null;

    for (FilterRuleDto rule : columnFilter.constraints()) {
      Specification<T> ruleSpec = buildRule(field, rule);

      if (acc == null) {
        acc = ruleSpec;
        continue;
      }

      acc = "or".equals(operator)
        ? acc.or(ruleSpec)
        : acc.and(ruleSpec);
    }

    return acc;
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private <T> Specification<T> buildRule(FieldSpec<T, ?> field, FilterRuleDto rule) {
    String matchMode = (rule == null || rule.matchMode() == null || rule.matchMode().isBlank())
      ? "contains"
      : rule.matchMode().trim();

    Object raw = rule == null ? null : rule.value();

    return (root, query, cb) -> {
      if (field.requiresDistinct()) {
        query.distinct(true);
      }

      Path path = field.path(root, query);

      if ("between".equals(matchMode)) {
        RangeValue<?> range = Converters.toRangeOrNull(raw, field::convert);

        if (range == null) {
          return cb.conjunction();
        }

        Object from = range.from();
        Object to = range.to();

        if (from instanceof OffsetDateTime fromOdt) {
          from = dateFilterService.startOfBusinessDay(fromOdt);
        }

        if (to instanceof OffsetDateTime toOdt) {
          to = dateFilterService.endOfBusinessDay(toOdt);
        }

        if (from == null && to == null) {
          return cb.conjunction();
        }

        Expression expression = path.as(field.valueType());

        if (from != null && to != null) {
          return cb.between(expression, (Comparable) from, (Comparable) to);
        }

        if (from != null) {
          return cb.greaterThanOrEqualTo(expression, (Comparable) from);
        }

        return cb.lessThanOrEqualTo(expression, (Comparable) to);
      }

      if ("in".equals(matchMode)) {
        Collection<?> collection = Converters.toCollectionOrNull(raw);

        if (collection == null || collection.isEmpty()) {
          return cb.conjunction();
        }

        var inClause = cb.in(path);
        boolean hasAnyValue = false;

        for (Object item : collection) {
          Object converted = field.convert(item);

          if (converted != null) {
            inClause.value(converted);
            hasAnyValue = true;
          }
        }

        return hasAnyValue ? inClause : cb.conjunction();
      }

      if (OffsetDateTime.class.equals(field.valueType())) {
        PeriodFilter periodFilter = toPeriodFilter(raw);

        if (periodFilter != null) {
          return buildOffsetDateTimePeriodPredicate(cb, path, periodFilter);
        }
      }

      Object typed = field.convert(raw);

      switch (typed) {
        case null -> {
          return cb.conjunction();
        }

        case String s -> {
          Expression<String> expression = cb.lower(path.as(String.class));
          String value = s.trim().toLowerCase();

          if (value.isBlank()) {
            return cb.conjunction();
          }

          return switch (matchMode) {
            case "equals" -> cb.equal(expression, value);
            case "notEquals" -> cb.notEqual(expression, value);
            case "startsWith" -> cb.like(expression, value + "%");
            case "endsWith" -> cb.like(expression, "%" + value);
            case "notStartsWith" -> cb.notLike(expression, value + "%");
            case "notEndsWith" -> cb.notLike(expression, "%" + value);
            case "notContains" -> cb.notLike(expression, "%" + value + "%");
            default -> cb.like(expression, "%" + value + "%");
          };
        }

        case OffsetDateTime dt -> {
          Expression<OffsetDateTime> expression = path.as(OffsetDateTime.class);

          return switch (matchMode) {
            case "dateBefore", "lt" ->
              cb.lessThan(expression, dateFilterService.startOfBusinessDay(dt));

            case "dateAfter", "gt" ->
              cb.greaterThan(expression, dateFilterService.endOfBusinessDay(dt));

            case "dateIs", "equals" -> {
              OffsetDateTime start = dateFilterService.startOfBusinessDay(dt);
              OffsetDateTime end = dateFilterService.endOfBusinessDay(dt);

              yield cb.and(
                cb.greaterThanOrEqualTo(expression, start),
                cb.lessThanOrEqualTo(expression, end)
              );
            }

            case "dateIsNot", "notEquals" -> {
              OffsetDateTime start = dateFilterService.startOfBusinessDay(dt);
              OffsetDateTime end = dateFilterService.endOfBusinessDay(dt);

              yield cb.or(
                cb.lessThan(expression, start),
                cb.greaterThan(expression, end)
              );
            }

            case "gte" ->
              cb.greaterThanOrEqualTo(expression, dateFilterService.startOfBusinessDay(dt));

            case "lte" ->
              cb.lessThanOrEqualTo(expression, dateFilterService.endOfBusinessDay(dt));

            default -> cb.equal(expression, dt);
          };
        }

        case Number n -> {
          Expression<Number> expression = path.as(Number.class);

          return switch (matchMode) {
            case "gt" -> cb.gt(expression, n);
            case "gte" -> cb.ge(expression, n);
            case "lt" -> cb.lt(expression, n);
            case "lte" -> cb.le(expression, n);
            case "notEquals" -> cb.notEqual(expression, n);
            default -> cb.equal(expression, n);
          };
        }

        default -> {
        }
      }

      return "notEquals".equals(matchMode)
        ? cb.notEqual(path, typed)
        : cb.equal(path, typed);
    };
  }

  private record PeriodFilter(PeriodEnum period, List<String> values) {
  }

  private record DateRange(LocalDate start, LocalDate end) {
  }

  private PeriodFilter toPeriodFilter(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) {
      return null;
    }

    PeriodEnum period = toPeriodEnum(map.get("period"));
    List<String> values = toStringList(map.get("value"));

    if (period == null || period == PeriodEnum.NULL || values.isEmpty()) {
      return null;
    }

    return new PeriodFilter(period, values);
  }

  private PeriodEnum toPeriodEnum(Object raw) {
    switch (raw) {
      case null -> {
        return null;
      }
      case PeriodEnum period -> {
        return period;
      }
      case Number number -> {
        try {
          return PeriodEnum.fromCode(number.intValue());
        } catch (Exception ignored) {
          return null;
        }
      }
      default -> {
      }
    }

    String value = String.valueOf(raw).trim();

    if (value.isBlank()) {
      return null;
    }

    try {
      return PeriodEnum.fromName(value);
    } catch (Exception ignored) {
      return null;
    }
  }

  private List<String> toStringList(Object raw) {
    if (raw == null) {
      return List.of();
    }

    if (raw instanceof Collection<?> collection) {
      return collection.stream()
        .filter(item -> item != null && !String.valueOf(item).isBlank())
        .map(item -> String.valueOf(item).trim())
        .toList();
    }

    if (raw.getClass().isArray()) {
      int length = Array.getLength(raw);
      List<String> values = new ArrayList<>(length);

      for (int i = 0; i < length; i++) {
        Object item = Array.get(raw, i);

        if (item != null && !String.valueOf(item).isBlank()) {
          values.add(String.valueOf(item).trim());
        }
      }

      return values;
    }

    String value = String.valueOf(raw).trim();

    return value.isBlank() ? List.of() : List.of(value);
  }

  private Predicate buildOffsetDateTimePeriodPredicate(
    CriteriaBuilder cb,
    Path<?> path,
    PeriodFilter filter
  ) {
    Expression<OffsetDateTime> expression = path.as(OffsetDateTime.class);

    return switch (filter.period()) {
      case DAY -> {
        LocalDate date = parseDate(first(filter.values()));

        yield date == null
          ? cb.conjunction()
          : cb.between(expression, startOfDay(date), endOfDay(date));
      }

      case START -> {
        LocalDate date = parseDate(first(filter.values()));

        yield date == null
          ? cb.conjunction()
          : cb.greaterThanOrEqualTo(expression, startOfDay(date));
      }

      case END -> {
        LocalDate date = parseDate(first(filter.values()));

        yield date == null
          ? cb.conjunction()
          : cb.lessThanOrEqualTo(expression, endOfDay(date));
      }

      case MONTH -> {
        YearMonth month = parseMonth(first(filter.values()));

        yield month == null
          ? cb.conjunction()
          : cb.between(
          expression,
          startOfDay(month.atDay(1)),
          endOfDay(month.atEndOfMonth())
        );
      }

      case YEAR -> {
        Year year = parseYear(first(filter.values()));

        yield year == null
          ? cb.conjunction()
          : cb.between(
          expression,
          startOfDay(year.atDay(1)),
          endOfDay(year.atMonth(12).atEndOfMonth())
        );
      }

      case INTERVAL -> {
        DateRange range = parseDateRange(filter.values());

        yield range == null
          ? cb.conjunction()
          : cb.between(expression, startOfDay(range.start()), endOfDay(range.end()));
      }

      case NULL -> cb.conjunction();
    };
  }

  private DateRange parseDateRange(List<String> values) {
    if (values == null || values.size() < 2) {
      return null;
    }

    LocalDate start = parseDate(values.get(0));
    LocalDate end = parseDate(values.get(1));

    if (start == null || end == null) {
      return null;
    }

    if (end.isBefore(start)) {
      return new DateRange(end, start);
    }

    return new DateRange(start, end);
  }

  private String first(List<String> values) {
    return values == null || values.isEmpty() ? null : values.getFirst();
  }

  private LocalDate parseDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    String value = raw.trim();

    try {
      return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    } catch (DateTimeParseException ignored) {
    }

    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException ignored) {
    }

    try {
      return OffsetDateTime.parse(value)
        .toInstant()
        .atZone(dateFilterService.businessZone())
        .toLocalDate();
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private YearMonth parseMonth(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    String value = raw.trim();

    try {
      return YearMonth.parse(value, DateTimeFormatter.ofPattern("MM/yyyy"));
    } catch (DateTimeParseException ignored) {
    }

    try {
      return YearMonth.parse(value);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private Year parseYear(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    try {
      return Year.parse(raw.trim());
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private OffsetDateTime startOfDay(LocalDate date) {
    return date
      .atStartOfDay(dateFilterService.businessZone())
      .toOffsetDateTime();
  }

  private OffsetDateTime endOfDay(LocalDate date) {
    return date
      .plusDays(1)
      .atStartOfDay(dateFilterService.businessZone())
      .minusNanos(1)
      .toOffsetDateTime();
  }
}