package com.cardsync.infrastructure.repository.spec.config;

import com.cardsync.domain.filter.query.RangeValue;

import java.lang.reflect.Array;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;

final class Converters {

  private Converters() {
  }

  static String toStringOrNull(Object v) {
    if (v == null) {
      return null;
    }

    String s = String.valueOf(v).trim();
    return s.isEmpty() ? null : s;
  }

  static Integer toIntegerOrNull(Object v) {
    if (v == null) {
      return null;
    }

    if (v instanceof Number n) {
      return n.intValue();
    }

    try {
      String s = String.valueOf(v).trim();
      if (s.isEmpty()) {
        return null;
      }
      return Integer.parseInt(s);
    } catch (Exception e) {
      return null;
    }
  }

  static Long toLongOrNull(Object v) {
    if (v == null) {
      return null;
    }

    if (v instanceof Number n) {
      return n.longValue();
    }

    try {
      String s = String.valueOf(v).trim();
      if (s.isEmpty()) {
        return null;
      }
      return Long.parseLong(s);
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