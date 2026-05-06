package com.cardsync.infrastructure.repository.spec.config;

import com.cardsync.domain.filter.query.RangeValue;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;

final class Converters {

  private Converters() {
  }

  private static Object singleValue(Object v) {
    if (v == null) {
      return null;
    }

    if (v instanceof Collection<?> collection) {
      if (collection.isEmpty()) {
        return null;
      }
      return collection.iterator().next();
    }

    if (v.getClass().isArray()) {
      return Array.getLength(v) == 0 ? null : Array.get(v, 0);
    }

    return v;
  }

  private static String normalizeNumericText(Object v) {
    Object single = singleValue(v);

    if (single == null) {
      return null;
    }

    String value = String.valueOf(single).trim();

    if (value.isBlank()) {
      return null;
    }

    return value
      .replace("R$", "")
      .replace("$", "")
      .replace("€", "")
      .replace("£", "")
      .replace("\u00A0", "")
      .replace(" ", "")
      .trim();
  }

  private static String normalizeDecimalText(Object v) {
    String value = normalizeNumericText(v);

    if (value == null) {
      return null;
    }

    boolean hasComma = value.contains(",");
    boolean hasDot = value.contains(".");

    if (hasComma && hasDot) {
      int lastComma = value.lastIndexOf(',');
      int lastDot = value.lastIndexOf('.');

      if (lastComma > lastDot) {
        return value.replace(".", "").replace(',', '.');
      }

      return value.replace(",", "");
    }

    if (hasComma) {
      return value.replace(',', '.');
    }

    return value;
  }

  static String toStringOrNull(Object v) {
    if (v == null) {
      return null;
    }

    String s = String.valueOf(v).trim();
    return s.isEmpty() ? null : s;
  }

  static Integer toIntegerOrNull(Object v) {
    Object single = singleValue(v);

    if (single == null) {
      return null;
    }

    if (single instanceof Number n) {
      double value = n.doubleValue();
      return value % 1 == 0 ? n.intValue() : null;
    }

    try {
      String text = normalizeDecimalText(single);
      if (text == null) {
        return null;
      }

      BigDecimal decimal = new BigDecimal(text);
      return decimal.stripTrailingZeros().scale() <= 0 ? decimal.intValueExact() : null;
    } catch (Exception e) {
      return null;
    }
  }

  static Long toLongOrNull(Object v) {
    Object single = singleValue(v);

    if (single == null) {
      return null;
    }

    if (single instanceof Number n) {
      double value = n.doubleValue();
      return value % 1 == 0 ? n.longValue() : null;
    }

    try {
      String text = normalizeDecimalText(single);
      if (text == null) {
        return null;
      }

      BigDecimal decimal = new BigDecimal(text);
      return decimal.stripTrailingZeros().scale() <= 0 ? decimal.longValueExact() : null;
    } catch (Exception e) {
      return null;
    }
  }

  static BigDecimal toBigDecimalOrNull(Object v) {
    Object single = singleValue(v);

    switch (single) {
      case null -> {
        return null;
      }
      case BigDecimal bd -> {
        return bd;
      }
      case Number n -> {
        return BigDecimal.valueOf(n.doubleValue());
      }
      default -> {
      }
    }

    try {
      String text = normalizeDecimalText(single);
      return text == null ? null : new BigDecimal(text);
    } catch (Exception e) {
      return null;
    }
  }

  static Boolean toBooleanOrNull(Object v) {
    if (v == null) {
      return null;
    }

    if (v instanceof Boolean b) {
      return b;
    }

    String s = String.valueOf(v).trim().toLowerCase();
    if (s.isEmpty()) {
      return null;
    }

    if ("true".equals(s) || "1".equals(s)) {
      return true;
    }

    if ("false".equals(s) || "0".equals(s)) {
      return false;
    }

    return null;
  }

  static OffsetDateTime toOffsetDateTimeOrNull(Object v, DateFilterService dateFilterService) {
    if (v == null) {
      return null;
    }

    if (v instanceof OffsetDateTime odt) {
      return odt;
    }

    String s = String.valueOf(v).trim();
    if (s.isEmpty()) {
      return null;
    }

    return dateFilterService.parseFlexibleToOffsetDateTime(s);
  }

  static LocalDate toLocalDateOrNull(Object v, DateFilterService dateFilterService) {
    if (v == null) {
      return null;
    }

    if (v instanceof LocalDate date) {
      return date;
    }

    if (v instanceof OffsetDateTime odt) {
      return odt
        .toInstant()
        .atZone(dateFilterService.businessZone())
        .toLocalDate();
    }

    OffsetDateTime parsed = toOffsetDateTimeOrNull(v, dateFilterService);

    return parsed == null
      ? null
      : parsed.toInstant().atZone(dateFilterService.businessZone()).toLocalDate();
  }

  static <E extends Enum<E>> E toEnumOrNull(Object v, Class<E> enumClass, Function<E, Integer> codeExtractor) {
    if (v == null) {
      return null;
    }

    if (enumClass.isInstance(v)) {
      return enumClass.cast(v);
    }

    Integer num = toIntegerOrNull(v);
    if (num != null) {
      E[] values = enumClass.getEnumConstants();

      if (codeExtractor != null) {
        for (E e : values) {
          Integer code = codeExtractor.apply(e);
          if (code != null && code.equals(num)) {
            return e;
          }
        }
      }

      if (num >= 0 && num < values.length) {
        return values[num];
      }

      return null;
    }

    String s = toStringOrNull(v);
    if (s == null) {
      return null;
    }

    try {
      return Enum.valueOf(enumClass, s.trim().toUpperCase());
    } catch (Exception ignored) {
      return null;
    }
  }

  static Collection<?> toCollectionOrNull(Object v) {
    if (v == null) {
      return null;
    }

    if (v instanceof Collection<?> c) {
      return c;
    }

    if (v.getClass().isArray()) {
      int len = Array.getLength(v);
      List<Object> out = new ArrayList<>(len);

      for (int i = 0; i < len; i++) {
        out.add(Array.get(v, i));
      }

      return out;
    }

    String s = toStringOrNull(v);
    if (s != null && s.contains(",")) {
      return Arrays.stream(s.split(","))
        .map(String::trim)
        .filter(x -> !x.isEmpty())
        .toList();
    }

    return List.of(v);
  }

  static <T> RangeValue<T> toRangeOrNull(Object v, Function<Object, T> converter) {
    switch (v) {
      case null -> {
        return null;
      }
      case RangeValue<?> rv -> {
        T from = rv.from()==null ? null:converter.apply(rv.from());
        T to = rv.to()==null ? null:converter.apply(rv.to());

        if (from==null && to==null) {
          return null;
        }

        return new RangeValue<>(from, to);
      }
      case Map<?, ?> map -> {
        Object rawFrom = map.get("from");
        Object rawTo = map.get("to");

        T from = rawFrom==null ? null:converter.apply(rawFrom);
        T to = rawTo==null ? null:converter.apply(rawTo);

        if (from==null && to==null) {
          return null;
        }

        return new RangeValue<>(from, to);
      }
      case Collection<?> collection -> {
        if (collection.isEmpty()) {
          return null;
        }

        Object[] items = collection.toArray();
        Object rawFrom = items[0];
        Object rawTo = items.length > 1 ? items[1]:null;

        T from = rawFrom==null ? null:converter.apply(rawFrom);
        T to = rawTo==null ? null:converter.apply(rawTo);

        if (from==null && to==null) {
          return null;
        }

        return new RangeValue<>(from, to);
      }
      default -> {
      }
    }

    if (v.getClass().isArray()) {
      int length = Array.getLength(v);
      if (length == 0) {
        return null;
      }

      Object rawFrom = length > 0 ? Array.get(v, 0) : null;
      Object rawTo = length > 1 ? Array.get(v, 1) : null;

      T from = rawFrom == null ? null : converter.apply(rawFrom);
      T to = rawTo == null ? null : converter.apply(rawTo);

      if (from == null && to == null) {
        return null;
      }

      return new RangeValue<>(from, to);
    }

    T single = converter.apply(v);
    if (single == null) {
      return null;
    }

    return new RangeValue<>(single, single);
  }

  static UUID toUuidOrNull(Object v) {
    if (v == null) {
      return null;
    }

    if (v instanceof UUID uuid) {
      return uuid;
    }

    String s = toStringOrNull(v);
    if (s == null) {
      return null;
    }

    try {
      return UUID.fromString(s);
    } catch (Exception e) {
      return null;
    }
  }
}