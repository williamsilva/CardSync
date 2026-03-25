package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.EmailLogFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.EmailLogAllowedFields;
import com.cardsync.domain.model.EmailLogEntity;
import com.cardsync.domain.model.enums.EmailLogEventTypeEnum;
import com.cardsync.domain.model.enums.EmailLogStatusEnum;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class EmailLogSpecs {

  private final DateFilterService dateFilterService;

  private EmailLogSpecs(DateFilterService dateFilterService) {
    this.dateFilterService = dateFilterService;
  }

  public Specification<EmailLogEntity> fromQuery(ListQueryDto<EmailLogFilter> query) {
    Specification<EmailLogEntity> spec = Specs.all();

    spec = spec.and(
      SpecificationFactory.fromTableFilters(
        query.tableFilters(),
        EmailLogAllowedFields.table()
      )
    );

    if (query.advanced()!=null) {
      var a = query.advanced();

      spec = spec.and(textContains("subject", a.getSubject()));
      spec = spec.and(textContains("template", a.getTemplate()));
      spec = spec.and(textContains("recipient", a.getRecipient()));

      if (a.getStatus() != null && !a.getStatus().isEmpty()) {
        var codes = a.getStatus().stream()
          .filter(Objects::nonNull)
          .map(EmailLogStatusEnum::getCode)
          .toList();

        if (!codes.isEmpty()) {
          spec = spec.and((root, q2, cb) -> root.get("status").in(codes));
        }
      }

      if (a.getEventType() != null && !a.getEventType().isEmpty()) {
        var codes = a.getEventType().stream()
          .filter(Objects::nonNull)
          .map(EmailLogEventTypeEnum::getCode)
          .toList();

        if (!codes.isEmpty()) {
          spec = spec.and((root, q2, cb) -> root.get("eventType").in(codes));
        }
      }

      spec = spec.and(rangeOdt("sentAt", a.getSentAtFrom(), a.getSentAtTo()));
    }

    return spec;
  }

  private Specification<EmailLogEntity> textContains(String field, String value) {
    if (value == null || value.trim().isEmpty()) {
      return Specs.all();
    }

    String v = value.trim().toLowerCase();

    return (root, query, cb) ->
      cb.like(cb.lower(root.get(field)), "%" + v + "%");
  }

  private Specification<EmailLogEntity> rangeOdt(String field, String fromIso, String toIso) {
    OffsetDateTime from = (fromIso == null || fromIso.isBlank())
      ? null
      : dateFilterService.startOfBusinessDay(fromIso);

    OffsetDateTime to = (toIso == null || toIso.isBlank())
      ? null
      : dateFilterService.endOfBusinessDay(toIso);

    if (from == null && to == null) {
      return Specs.all();
    }

    return (root, query, cb) -> {
      var p = root.get(field).as(OffsetDateTime.class);

      if (from != null && to != null) {
        return cb.between(p, from, to);
      }

      if (from != null) {
        return cb.greaterThanOrEqualTo(p, from);
      }

      return cb.lessThanOrEqualTo(p, to);
    };
  }
}