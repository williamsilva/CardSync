package com.cardsync.domain.service.support;

import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentTotalsModel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AdjustmentTotalsQueryService {

  @PersistenceContext
  private EntityManager entityManager;

  public <T> AdjustmentTotalsModel totals(Class<T> entityClass, Specification<T> spec, String totalField) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);

    Expression<BigDecimal> totalValue = root.get(totalField).as(BigDecimal.class);
    cq.multiselect(sumOrZero(cb, totalValue).alias("totalValue"),  cb.count(root).alias("quantity"));

    if (spec != null) {
      Predicate predicate = spec.toPredicate(root, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    Tuple tuple = entityManager.createQuery(cq).getSingleResult();

    return new AdjustmentTotalsModel(
      value(tuple, "totalValue"),
      tuple.get("quantity", Long.class)
    );
  }

  private static Expression<BigDecimal> sumOrZero(CriteriaBuilder cb, Expression<BigDecimal> expression) {
    return cb.coalesce(cb.sum(expression), BigDecimal.ZERO);
  }

  private static BigDecimal value(Tuple tuple, String alias) {
    BigDecimal value = tuple.get(alias, BigDecimal.class);
    return value == null ? BigDecimal.ZERO : value;
  }
}
