package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.EmailLogFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.EmailLogAllowedFields;
import com.cardsync.domain.model.EmailLogEntity;
import com.cardsync.domain.model.enums.EmailLogEventTypeEnum;
import com.cardsync.domain.model.enums.EmailLogStatusEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class EmailLogSpecs extends BaseSpecificationSupport<EmailLogEntity> {

  private final SpecificationFactory specificationFactory;
  private final EmailLogAllowedFields emailLogAllowedFields;

  private EmailLogSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    EmailLogAllowedFields emailLogAllowedFields
    ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.emailLogAllowedFields = emailLogAllowedFields;
  }

  public Specification<EmailLogEntity> fromQuery(ListQueryDto<EmailLogFilter> query) {
    Specification<EmailLogEntity> spec = Specs.all();

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        emailLogAllowedFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();

      spec = spec.and(contains("subject", a.subject()));
      spec = spec.and(contains("template", a.template()));
      spec = spec.and(contains("recipient", a.recipient()));

      spec = spec.and(rangeOdt("sentAt", a.sentAtFrom(), a.sentAtTo()));
      spec = spec.and(inCodes("status", a.status(), EmailLogStatusEnum::getCode));
      spec = spec.and(inCodes("eventType", a.eventType(), EmailLogEventTypeEnum::getCode));

      spec = spec.and(
        inPath(a.createdBy(), value -> {
          try {
            return UUID.fromString(value);
          } catch (Exception e) {
            return null;
          }
        }, "createdBy", "id")
      );

    }
    spec = spec.and(orderByAsc("createdAt"));

    return spec;
  }

}