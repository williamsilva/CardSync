package com.cardsync.infrastructure.repository.spec.config;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

import java.util.function.BiFunction;
import java.util.function.Function;

public final class FieldSpec<T, V> {
  private final String publicName;
  private final Class<V> valueType;
  private final BiFunction<Root<T>, CriteriaQuery<?>, Path<V>> pathFn;
  private final Function<Object, V> converter;
  private final boolean requiresDistinct; // quando usar join, marque true

  private FieldSpec(
    String publicName,
    Class<V> valueType,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<V>> pathFn,
    Function<Object, V> converter,
    boolean requiresDistinct
  ) {
    this.publicName = publicName;
    this.valueType = valueType;
    this.pathFn = pathFn;
    this.converter = converter;
    this.requiresDistinct = requiresDistinct;
  }

  public String publicName() { return publicName; }
  public Class<V> valueType() { return valueType; }
  public boolean requiresDistinct() { return requiresDistinct; }

  public Path<V> path(Root<T> root, CriteriaQuery<?> query) {
    return pathFn.apply(root, query);
  }

  public V convert(Object raw) {
    return converter.apply(raw);
  }

  // ========= factories =========

  public static <T> FieldSpec<T, String> string(String name, Function<Root<T>, Path<String>> path) {
    return new FieldSpec<>(name, String.class, (r, q) -> path.apply(r), Converters::toStringOrNull, false);
  }

  public static <T> FieldSpec<T, Integer> integer(String name, Function<Root<T>, Path<Integer>> path) {
    return new FieldSpec<>(name, Integer.class, (r, q) -> path.apply(r), Converters::toIntegerOrNull, false);
  }

  public static <T> FieldSpec<T, Long> longNum(String name, Function<Root<T>, Path<Long>> path) {
    return new FieldSpec<>(name, Long.class, (r, q) -> path.apply(r), Converters::toLongOrNull, false);
  }

  public static <T> FieldSpec<T, Boolean> bool(String name, Function<Root<T>, Path<Boolean>> path) {
    return new FieldSpec<>(name, Boolean.class, (r, q) -> path.apply(r), Converters::toBooleanOrNull, false);
  }

  public static <T> FieldSpec<T, java.time.OffsetDateTime> offsetDateTime(String name, Function<Root<T>, Path<java.time.OffsetDateTime>> path) {
    return new FieldSpec<>(name, java.time.OffsetDateTime.class, (r, q) -> path.apply(r), Converters::toOffsetDateTimeOrNull, false);
  }

  /** Enum vindo do front (nome/code) mas persistido como INTEGER no banco */
  public static <T, E extends Enum<E>> FieldSpec<T, Integer> enumCodeByNameOrCode(
    String name,
    Class<E> enumClass,
    Function<E, Integer> codeExtractor,
    Function<Root<T>, Path<Integer>> path
  ) {
    return new FieldSpec<>(
      name,
      Integer.class,
      (r, q) -> path.apply(r),
      raw -> {
        E e = Converters.toEnumOrNull(raw, enumClass, codeExtractor);
        return e == null ? null : codeExtractor.apply(e);
      },
      false
    );
  }

  /** Campo string via join: ex root.joinSet("groups", LEFT).get("name") */
  public static <T> FieldSpec<T, String> joinedString(
    String name,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<String>> pathFnDistinctJoin
  ) {
    return new FieldSpec<>(name, String.class, pathFnDistinctJoin, Converters::toStringOrNull, true);
  }

  public static <T, V> FieldSpec<T, V> of(
    String name,
    Class<V> type,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<V>> pathFn,
    Function<Object, V> converter,
    boolean requiresDistinct
  ) {
    return new FieldSpec<>(name, type, pathFn, converter, requiresDistinct);
  }
}
