package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.TransactionAcqSalesFilter;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionAcqAdvancedFields extends BaseSpecificationSupport<TransactionAcqEntity> {

  public TransactionAcqAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<TransactionAcqEntity> advanced(TransactionAcqSalesFilter filter) {
    if (filter == null) {
      return Specs.all();
    }

    Specification<TransactionAcqEntity> spec = Specs.all();

    spec = spec.and(contains(filter.tid(), "tid"));
    spec = spec.and(contains(filter.cvNsu(), "nsu"));
    spec = spec.and(contains(filter.machine(), "machine"));
    spec = spec.and(contains(filter.cardNumber(), "cardNumber"));
    spec = spec.and(contains(filter.authorization(), "authorization"));

    spec = spec.and(flag(filter));
    spec = spec.and(company(filter));
    spec = spec.and(capture(filter));
    spec = spec.and(saleDate(filter));
    spec = spec.and(modality(filter));
    spec = spec.and(acquirer(filter));
    spec = spec.and(establishment(filter));
    spec = spec.and(transactionStatus(filter));
    spec = spec.and(expectedPaymentDate(filter));
    spec = spec.and(adjustmentValue(filter.adjustmentValueStart(), filter.adjustmentValueEnd()));
    spec = spec.and(currencyRangeValue("grossValue", filter.grossValueStart(), filter.grossValueEnd()));
    spec = spec.and(currencyRangeValue("liquidValue", filter.liquidValueStart(), filter.liquidValueEnd()));
    spec = spec.and(currencyRangeValue("discountValue", filter.discountValueStart(), filter.discountValueEnd()));

    return spec;
  }

  private Specification<TransactionAcqEntity> adjustmentValue(BigDecimal start, BigDecimal end) {
    return currencyRangeValue("adjustment", "adjustmentValue", start, end, BigDecimal.ZERO);
  }

  protected Specification<TransactionAcqEntity> currencyRangeValue(String field, BigDecimal start, BigDecimal end) {
    return currencyRangeValue(null, field, start, end, null);
  }

  private Specification<TransactionAcqEntity> currencyRangeValue(
    String association, String field, BigDecimal start, BigDecimal end, BigDecimal nullAs) {
    if (start == null && end == null) {
      return alwaysTrue();
    }

    if (start != null && end != null && end.compareTo(start) < 0) {
      BigDecimal tmp = start;
      start = end;
      end = tmp;
    }

    BigDecimal finalStart = start;
    BigDecimal finalEnd = end;

    return (root, query, cb) -> {
      Expression<BigDecimal> path;

      if (association == null || association.isBlank()) {
        path = root.get(field).as(BigDecimal.class);
      } else {
        path = root.join(association, JoinType.LEFT).get(field).as(BigDecimal.class);
      }

      if (nullAs != null) {
        path = cb.coalesce(path, nullAs);
      }

      if (finalStart != null && finalEnd != null) {
        return cb.between(path, finalStart, finalEnd);
      }

      if (finalStart != null) {
        return cb.greaterThanOrEqualTo(path, finalStart);
      }

      return cb.lessThanOrEqualTo(path, finalEnd);
    };
  }

  private Specification<TransactionAcqEntity> saleDate(TransactionAcqSalesFilter filter) {
    return datePeriod(
      "saleDate",
      filter.periodSaleDate(),
      filter.saleDate(),
      true
    );
  }

  private Specification<TransactionAcqEntity> expectedPaymentDate(TransactionAcqSalesFilter filter) {
    return localDatePeriodJoin(
      "installments",
      "expectedPaymentDate",
      filter.periodExpectedPaymentDate(),
      filter.expectedPaymentDate(),
      true
    );
  }

  private Specification<TransactionAcqEntity> modality(TransactionAcqSalesFilter filter) {
    return inCodes(
      "modality",
      filter.modality(),
      ModalityEnum::getCode
    );
  }

  private Specification<TransactionAcqEntity> transactionStatus(TransactionAcqSalesFilter filter) {
    return inCodes(
      "transactionStatus",
      filter.transactionStatus(),
      StatusTransactionEnum::getCode
    );
  }

  private Specification<TransactionAcqEntity> capture(TransactionAcqSalesFilter filter) {
    return inCodes(
      "capture",
      filter.capture(),
      CaptureEnum::getCode
    );
  }

  private Specification<TransactionAcqEntity> company(TransactionAcqSalesFilter filter) {
    return inPath(
      filter.companies(),
      TransactionAcqAdvancedFields::parseUuidOrNull,
      "company",
      "id"
    );
  }

  private Specification<TransactionAcqEntity> establishment(TransactionAcqSalesFilter filter) {
    return inPath(
      filter.establishments(),
      TransactionAcqAdvancedFields::parseUuidOrNull,
      "establishment",
      "id"
    );
  }

  private Specification<TransactionAcqEntity> acquirer(TransactionAcqSalesFilter filter) {
    return inPath(
      filter.acquirers(),
      TransactionAcqAdvancedFields::parseUuidOrNull,
      "acquirer",
      "id"
    );
  }

  private Specification<TransactionAcqEntity> flag(TransactionAcqSalesFilter filter) {
    return inPath(
      filter.flags(),
      TransactionAcqAdvancedFields::parseUuidOrNull,
      "flag",
      "id"
    );
  }
}
