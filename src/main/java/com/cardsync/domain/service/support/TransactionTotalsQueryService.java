package com.cardsync.domain.service.support;

import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionTotalsQueryService {

  @PersistenceContext
  private EntityManager entityManager;

  public <T> TransactionTotalsModel totals(
    Class<T> entityClass,
    Specification<T> spec,
    String grossField,
    String feeField,
    String netField
  ) {
    return totals(entityClass, spec, grossField, feeField, netField, null, null);
  }

  public <T> TransactionTotalsModel totals(
    Class<T> entityClass,
    Specification<T> spec,
    String grossField,
    String feeField,
    String netField,
    String adjustmentAssociation,
    String adjustmentField
  ) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);

    Expression<BigDecimal> gross = root.get(grossField).as(BigDecimal.class);
    Expression<BigDecimal> fee = root.get(feeField).as(BigDecimal.class);
    Expression<BigDecimal> net = root.get(netField).as(BigDecimal.class);
    Expression<BigDecimal> adjustment = adjustmentExpression(cb, root, adjustmentAssociation, adjustmentField);

    cq.select(cb.tuple(
      sumOrZero(cb, gross).alias("totalGross"),
      sumOrZero(cb, fee).alias("totalFee"),
      sumOrZero(cb, net).alias("totalNet"),
      sumOrZero(cb, adjustment).alias("totalAdjustment"),
      cb.count(root).alias("quantity")
    ));

    if (spec != null) {
      Predicate predicate = spec.toPredicate(root, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    Tuple tuple = entityManager.createQuery(cq).getSingleResult();

    return new TransactionTotalsModel(
      value(tuple, "totalGross"),
      value(tuple, "totalFee"),
      value(tuple, "totalNet"),
      value(tuple, "totalAdjustment"),
      tuple.get("quantity", Long.class)
    );
  }

  private static <T> Expression<BigDecimal> adjustmentExpression(
    CriteriaBuilder cb,
    Root<T> root,
    String adjustmentAssociation,
    String adjustmentField
  ) {
    if (adjustmentField == null || adjustmentField.isBlank()) {
      return cb.literal(BigDecimal.ZERO);
    }

    if (adjustmentAssociation == null || adjustmentAssociation.isBlank()) {
      return root.<BigDecimal>get(adjustmentField).as(BigDecimal.class);
    }

    Path<BigDecimal> path = root.join(adjustmentAssociation, JoinType.LEFT).get(adjustmentField);
    return path.as(BigDecimal.class);
  }

  private static Expression<BigDecimal> sumOrZero(CriteriaBuilder cb, Expression<BigDecimal> expression) {
    return cb.coalesce(cb.sum(expression), BigDecimal.ZERO);
  }

  private static BigDecimal value(Tuple tuple, String alias) {
    BigDecimal value = tuple.get(alias, BigDecimal.class);
    return value == null ? BigDecimal.ZERO : value;
  }
}
