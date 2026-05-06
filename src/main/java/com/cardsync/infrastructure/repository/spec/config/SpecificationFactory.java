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
import java.math.BigDecimal;
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
          return buildOffsetDateTimePeriodPredicate(cb, path, periodFilter, matchMode);
        }
      }

      if (LocalDate.class.equals(field.valueType())) {
        PeriodFilter periodFilter = toPeriodFilter(raw);

        if (periodFilter != null) {
          return buildLocalDatePeriodPredicate(cb, path, periodFilter, matchMode);
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
          OffsetDateTime start = dateFilterService.startOfBusinessDay(dt);
          OffsetDateTime end = dateFilterService.endOfBusinessDay(dt);

          return buildComparableRangePredicate(cb, expression, start, end, matchMode);
        }

        case LocalDate date -> {
          Expression<LocalDate> expression = path.as(LocalDate.class);

          return buildComparableRangePredicate(cb, expression, date, date, matchMode);
        }

        case Number n -> {
          return buildNumberPredicate(cb, path, field.valueType(), n, matchMode);
        }

        default -> {
        }
      }

      return "notEquals".equals(matchMode)
        ? cb.notEqual(path, typed)
        : cb.equal(path, typed);
    };
  }


  @SuppressWarnings({ "unchecked", "rawtypes" })
  private Predicate buildNumberPredicate(
    CriteriaBuilder cb,
    Path<?> path,
    Class<?> valueType,
    Number value,
    String matchMode
  ) {
    if (isTextNumberMatchMode(matchMode)) {
      Expression<String> expression = path.as(String.class);
      String textValue = numberText(value);

      return switch (matchMode) {
        case "startsWith" -> cb.like(expression, textValue + "%");
        case "endsWith" -> cb.like(expression, "%" + textValue);
        case "notContains" -> cb.notLike(expression, "%" + textValue + "%");
        default -> cb.like(expression, "%" + textValue + "%");
      };
    }

    if (Integer.class.equals(valueType)) {
      Expression<Integer> expression = path.as(Integer.class);
      Integer typed = value.intValue();

      return switch (matchMode) {
        case "gt" -> cb.greaterThan(expression, typed);
        case "gte" -> cb.greaterThanOrEqualTo(expression, typed);
        case "lt" -> cb.lessThan(expression, typed);
        case "lte" -> cb.lessThanOrEqualTo(expression, typed);
        case "notEquals" -> cb.notEqual(expression, typed);
        default -> cb.equal(expression, typed);
      };
    }

    if (Long.class.equals(valueType)) {
      Expression<Long> expression = path.as(Long.class);
      Long typed = value.longValue();

      return switch (matchMode) {
        case "gt" -> cb.greaterThan(expression, typed);
        case "gte" -> cb.greaterThanOrEqualTo(expression, typed);
        case "lt" -> cb.lessThan(expression, typed);
        case "lte" -> cb.lessThanOrEqualTo(expression, typed);
        case "notEquals" -> cb.notEqual(expression, typed);
        default -> cb.equal(expression, typed);
      };
    }

    if (BigDecimal.class.equals(valueType)) {
      Expression<BigDecimal> expression = path.as(BigDecimal.class);
      BigDecimal typed = toBigDecimal(value);

      return switch (matchMode) {
        case "gt" -> cb.greaterThan(expression, typed);
        case "gte" -> cb.greaterThanOrEqualTo(expression, typed);
        case "lt" -> cb.lessThan(expression, typed);
        case "lte" -> cb.lessThanOrEqualTo(expression, typed);
        case "notEquals" -> cb.notEqual(expression, typed);
        default -> cb.equal(expression, typed);
      };
    }

    Expression<Double> expression = path.as(Double.class);
    Double typed = value.doubleValue();

    return switch (matchMode) {
      case "gt" -> cb.greaterThan(expression, typed);
      case "gte" -> cb.greaterThanOrEqualTo(expression, typed);
      case "lt" -> cb.lessThan(expression, typed);
      case "lte" -> cb.lessThanOrEqualTo(expression, typed);
      case "notEquals" -> cb.notEqual(expression, typed);
      default -> cb.equal(expression, typed);
    };
  }

  private boolean isTextNumberMatchMode(String matchMode) {
    return "contains".equals(matchMode)
      || "notContains".equals(matchMode)
      || "startsWith".equals(matchMode)
      || "endsWith".equals(matchMode);
  }

  private String numberText(Number value) {
    if (value instanceof BigDecimal bd) {
      return bd.stripTrailingZeros().toPlainString();
    }

    return String.valueOf(value);
  }

  private BigDecimal toBigDecimal(Number value) {
    if (value instanceof BigDecimal bd) {
      return bd;
    }

    return new BigDecimal(String.valueOf(value));
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
    PeriodFilter filter,
    String matchMode
  ) {
    Expression<OffsetDateTime> expression = path.as(OffsetDateTime.class);

    DateRange range = resolveDateRange(filter);

    if (range == null) {
      return cb.conjunction();
    }

    OffsetDateTime start = range.start() == null ? null : startOfDay(range.start());
    OffsetDateTime end = range.end() == null ? null : endOfDay(range.end());

    return buildComparableRangePredicate(cb, expression, start, end, matchMode);
  }

  private Predicate buildLocalDatePeriodPredicate(
    CriteriaBuilder cb,
    Path<?> path,
    PeriodFilter filter,
    String matchMode
  ) {
    Expression<LocalDate> expression = path.as(LocalDate.class);

    DateRange range = resolveDateRange(filter);

    if (range == null) {
      return cb.conjunction();
    }

    return buildComparableRangePredicate(cb, expression, range.start(), range.end(), matchMode);
  }

  private DateRange resolveDateRange(PeriodFilter filter) {
    return switch (filter.period()) {
      case DAY -> {
        LocalDate date = parseDate(first(filter.values()));
        yield date == null ? null : new DateRange(date, date);
      }

      case START -> {
        LocalDate date = parseDate(first(filter.values()));
        yield date == null ? null : new DateRange(date, null);
      }

      case END -> {
        LocalDate date = parseDate(first(filter.values()));
        yield date == null ? null : new DateRange(null, date);
      }

      case MONTH -> {
        YearMonth month = parseMonth(first(filter.values()));
        yield month == null ? null : new DateRange(month.atDay(1), month.atEndOfMonth());
      }

      case YEAR -> {
        Year year = parseYear(first(filter.values()));
        yield year == null ? null : new DateRange(year.atDay(1), year.atMonth(12).atEndOfMonth());
      }

      case INTERVAL -> parseDateRange(filter.values());

      case NULL -> null;
    };
  }

  private <Y extends Comparable<? super Y>> Predicate buildComparableRangePredicate(
    CriteriaBuilder cb,
    Expression<Y> expression,
    Y start,
    Y end,
    String matchMode
  ) {
    if (start == null && end == null) {
      return cb.conjunction();
    }

    if (start != null && end != null && end.compareTo(start) < 0) {
      Y tmp = start;
      start = end;
      end = tmp;
    }

    return switch (matchMode) {
      case "dateBefore", "lt" ->
        start == null ? cb.conjunction() : cb.lessThan(expression, start);

      case "dateAfter", "gt" ->
        end == null ? cb.conjunction() : cb.greaterThan(expression, end);

      case "dateIsNot", "notEquals" -> {
        if (start != null && end != null) {
          yield cb.or(
            cb.lessThan(expression, start),
            cb.greaterThan(expression, end)
          );
        }

        if (start != null) {
          yield cb.lessThan(expression, start);
        }

        yield cb.greaterThan(expression, end);
      }

      case "gte" ->
        start == null ? cb.conjunction() : cb.greaterThanOrEqualTo(expression, start);

      case "lte" ->
        end == null ? cb.conjunction() : cb.lessThanOrEqualTo(expression, end);

      default -> {
        if (start != null && end != null) {
          yield cb.between(expression, start, end);
        }

        if (start != null) {
          yield cb.greaterThanOrEqualTo(expression, start);
        }

        yield cb.lessThanOrEqualTo(expression, end);
      }
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