package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.TransactionErpSalesFilter;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class TransactionErpAdvancedFields extends BaseSpecificationSupport<TransactionErpEntity> {

  public TransactionErpAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<TransactionErpEntity> advanced(TransactionErpSalesFilter filter) {
    if (filter == null) {
      return Specs.all();
    }

    Specification<TransactionErpEntity> spec = Specs.all();

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
    spec = spec.and(modality(filter));
    spec = spec.and(acquirer(filter));
    spec = spec.and(establishment(filter));
    spec = spec.and(expectedPaymentDate(filter));
    //spec = spec.and(conciliationStatus(filter));

    return spec;
  }

  private Specification<TransactionErpEntity> saleDate(TransactionErpSalesFilter filter) {
    return datePeriod(
      "saleDate",
      filter.periodSaleDate(),
      filter.saleDate(),
      true
    );
  }

  private Specification<TransactionErpEntity> expectedPaymentDate(TransactionErpSalesFilter filter) {
    return datePeriodJoin(
      "installments",
      "creditDate",
      filter.periodExpectedPaymentDate(),
      filter.expectedPaymentDate(),
      true
    );
  }

  private Specification<TransactionErpEntity> modality(TransactionErpSalesFilter filter) {
    return inCodes(
      "modality",
      filter.modality(),
      ModalityEnum::getCode
    );
  }

  private Specification<TransactionErpEntity> capture(TransactionErpSalesFilter filter) {
    return inCodes(
      "capture",
      filter.capture(),
      CaptureEnum::getCode
    );
  }

  private Specification<TransactionErpEntity> company(TransactionErpSalesFilter filter) {
    return inPath(
      filter.companies(),
      TransactionErpAdvancedFields::parseUuidOrNull,
      "company",
      "id"
    );
  }

  private Specification<TransactionErpEntity> establishment(TransactionErpSalesFilter filter) {
    return inPath(
      filter.establishments(),
      TransactionErpAdvancedFields::parseUuidOrNull,
      "establishment",
      "id"
    );
  }

  private Specification<TransactionErpEntity> acquirer(TransactionErpSalesFilter filter) {
    return inPath(
      filter.acquirers(),
      TransactionErpAdvancedFields::parseUuidOrNull,
      "acquirer",
      "id"
    );
  }

  private Specification<TransactionErpEntity> flag(TransactionErpSalesFilter filter) {
    return inPath(
      filter.flags(),
      TransactionErpAdvancedFields::parseUuidOrNull,
      "flag",
      "id"
    );
  }
}