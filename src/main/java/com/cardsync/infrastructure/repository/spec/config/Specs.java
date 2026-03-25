package com.cardsync.infrastructure.repository.spec.config;

import org.springframework.data.jpa.domain.Specification;

public final class Specs {
  private Specs() {}

  /** sempre verdadeiro */
  public static <T> Specification<T> all() {
    return (root, query, cb) -> cb.conjunction();
  }

  /** sempre falso (útil em alguns casos) */
  public static <T> Specification<T> none() {
    return (root, query, cb) -> cb.disjunction();
  }
}
