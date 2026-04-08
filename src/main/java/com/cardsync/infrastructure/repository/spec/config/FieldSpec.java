package com.cardsync.infrastructure.repository.spec.config;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

public record FieldSpec<T, V>(
  String name,
  Class<V> valueType,
  BiFunction<Root<T>, CriteriaQuery<?>, Path<?>> pathResolver,
  Function<Object, V> converter,
  boolean requiresDistinct
) {

  public Path<?> path(Root<T> root, CriteriaQuery<?> query) {
    return pathResolver.apply(root, query);
  }

  public V convert(Object raw) {
    return converter.apply(raw);
  }

  public static <T> FieldSpec<T, String> string(
    String name,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<?>> pathResolver
  ) {
    return new FieldSpec<>(name, String.class, pathResolver, Converters::toStringOrNull, false);
  }

  public static <T, E extends Enum<E>> FieldSpec<T, Integer> enumAsIntegerCode(
    String name,
    Class<E> enumClass,
    Function<E, Integer> codeExtractor,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<?>> pathResolver
  ) {
    return new FieldSpec<>(
      name,
      Integer.class,
      pathResolver,
      raw -> {
        E e = Converters.toEnumOrNull(raw, enumClass, codeExtractor);
        return e == null ? null : codeExtractor.apply(e);
      },
      false
    );
  }

  public static <T> FieldSpec<T, Integer> integer(
    String name,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<?>> pathResolver
  ) {
    return new FieldSpec<>(name, Integer.class, pathResolver, Converters::toIntegerOrNull, false);
  }

  public static <T> FieldSpec<T, Long> longNumber(
    String name,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<?>> pathResolver
  ) {
    return new FieldSpec<>(name, Long.class, pathResolver, Converters::toLongOrNull, false);
  }

  public static <T> FieldSpec<T, Boolean> bool(
    String name,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<?>> pathResolver
  ) {
    return new FieldSpec<>(name, Boolean.class, pathResolver, Converters::toBooleanOrNull, false);
  }

  public static <T> FieldSpec<T, UUID> uuid(
    String name,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<?>> pathResolver
  ) {
    return new FieldSpec<>(name, UUID.class, pathResolver, Converters::toUuidOrNull, false);
  }

  public static <T> FieldSpec<T, UUID> joinedUuid(
    String name,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<?>> pathResolver
  ) {
    return new FieldSpec<>(name, UUID.class, pathResolver, Converters::toUuidOrNull, false);
  }

  public static <T> FieldSpec<T, OffsetDateTime> offsetDateTime(
    String name,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<?>> pathResolver,
    DateFilterService dateFilterService
  ) {
    return new FieldSpec<>(
      name,
      OffsetDateTime.class,
      pathResolver,
      raw -> Converters.toOffsetDateTimeOrNull(raw, dateFilterService),
      false
    );
  }

  public static <T, E extends Enum<E>> FieldSpec<T, E> enumCodeByNameOrCode(
    String name,
    Class<E> enumClass,
    Function<E, Integer> codeExtractor,
    BiFunction<Root<T>, CriteriaQuery<?>, Path<?>> pathResolver
  ) {
    return new FieldSpec<>(
      name,
      enumClass,
      pathResolver,
      raw -> Converters.toEnumOrNull(raw, enumClass, codeExtractor),
      false
    );
  }
}