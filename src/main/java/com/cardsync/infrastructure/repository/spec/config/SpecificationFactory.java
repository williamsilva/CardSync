package com.cardsync.infrastructure.repository.spec.config;

import com.cardsync.domain.filter.query.ColumnFilterDto;
import com.cardsync.domain.filter.query.FilterRuleDto;
import com.cardsync.domain.filter.query.RangeValue;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

public final class SpecificationFactory {

  private SpecificationFactory() {}

  public static <T> Specification<T> fromTableFilters(
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
        continue;
      }

      spec = spec.and(applyColumnFilter(fieldSpec, columnFilter));
    }

    return spec;
  }

  private static <T> Specification<T> applyColumnFilter(
    FieldSpec<T, ?> field,
    ColumnFilterDto columnFilter
  ) {
    if (columnFilter == null
      || columnFilter.constraints() == null
      || columnFilter.constraints().isEmpty()) {
      return Specs.all();
    }

    String operator = columnFilter.operator() == null
      ? "and"
      : columnFilter.operator().trim().toLowerCase();

    List<FilterRuleDto> rules = columnFilter.constraints();

    Specification<T> acc = null;

    for (FilterRuleDto rule : rules) {
      Specification<T> ruleSpec = buildRule(field, rule);

      if (acc == null) {
        acc = ruleSpec;
      } else if ("or".equals(operator)) {
        acc = acc.or(ruleSpec);
      } else {
        acc = acc.and(ruleSpec);
      }
    }

    return acc;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static <T> Specification<T> buildRule(
    FieldSpec<T, ?> field,
    FilterRuleDto rule
  ) {
    String matchMode = (rule == null || rule.matchMode() == null || rule.matchMode().isBlank())
      ? "contains"
      : rule.matchMode().trim();

    Object raw = rule == null ? null : rule.value();

    return (root, query, cb) -> {

      if (field.requiresDistinct()) {
        query.distinct(true);
      }

      Path path = field.path(root, query);

      // between / range
      RangeValue<?> range = Converters.toRangeOrNull(raw, field::convert);
      if (range != null || "between".equals(matchMode)) {
        if (range == null) {
          return cb.conjunction();
        }

        Object from = range.from();
        Object to = range.to();

        if (from instanceof OffsetDateTime fromOdt) {
          from = DateFilterUtils.startOfBusinessDay(fromOdt);
        }

        if (to instanceof OffsetDateTime toOdt) {
          to = DateFilterUtils.endOfBusinessDay(toOdt);
        }

        if (from == null && to == null) {
          return cb.conjunction();
        }

        Expression<? extends Comparable> expression = path.as(Comparable.class);

        Comparable fromValue = (Comparable) from;
        Comparable toValue = (Comparable) to;

        if (fromValue != null && toValue != null) {
          return cb.between(expression, fromValue, toValue);
        }

        if (fromValue != null) {
          return cb.greaterThanOrEqualTo(expression, fromValue);
        }

        return cb.lessThanOrEqualTo(expression, toValue);
      }

      // in
      if ("in".equals(matchMode)) {
        Collection<?> collection = Converters.toCollectionOrNull(raw);
        if (collection == null || collection.isEmpty()) {
          return cb.conjunction();
        }

        var inClause = cb.in(path);

        for (Object item : collection) {
          Object converted = field.convert(item);
          if (converted != null) {
            inClause.value(converted);
          }
        }

        return inClause;
      }

      Object typed = field.convert(raw);
      switch (typed) {
        case null -> {
          return cb.conjunction();
        }


        // string
        case String s -> {
          Expression<String> expression = cb.lower(path.as(String.class));
          String value = s.trim().toLowerCase();

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


        // OffsetDateTime
        case OffsetDateTime dt -> {
          Expression<OffsetDateTime> expression = path.as(OffsetDateTime.class);

          return switch (matchMode) {
            case "dateBefore", "lt" -> cb.lessThan(expression, DateFilterUtils.startOfBusinessDay(dt));

            case "dateAfter", "gt" -> cb.greaterThan(expression, DateFilterUtils.endOfBusinessDay(dt));

            case "dateIs", "equals" -> {
              OffsetDateTime start = DateFilterUtils.startOfBusinessDay(dt);
              OffsetDateTime end = DateFilterUtils.endOfBusinessDay(dt);

              yield cb.and(
                cb.greaterThanOrEqualTo(expression, start),
                cb.lessThanOrEqualTo(expression, end)
              );
            }

            case "dateIsNot", "notEquals" -> {
              OffsetDateTime start = DateFilterUtils.startOfBusinessDay(dt);
              OffsetDateTime end = DateFilterUtils.endOfBusinessDay(dt);

              yield cb.or(
                cb.lessThan(expression, start),
                cb.greaterThan(expression, end)
              );
            }

            case "gte" -> cb.greaterThanOrEqualTo(expression, DateFilterUtils.startOfBusinessDay(dt));

            case "lte" -> cb.lessThanOrEqualTo(expression, DateFilterUtils.endOfBusinessDay(dt));

            default -> cb.equal(expression, dt);
          };
        }


        // number
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

      // boolean / enum / fallback
      return "notEquals".equals(matchMode)
        ? cb.notEqual(path, typed)
        : cb.equal(path, typed);
    };
  }
}
