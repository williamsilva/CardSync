package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.HolidayFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.HolidayEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.tableFilters.HolidayTableFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class HolidaySpecs extends BaseSpecificationSupport<HolidayEntity> {

  private final HolidayTableFields holidayTableFields;
  private final SpecificationFactory specificationFactory;

  public HolidaySpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    HolidayTableFields holidayTableFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.holidayTableFields = holidayTableFields;
  }

  public Specification<HolidayEntity> fromQuery(ListQueryDto<HolidayFilter> query) {
    Specification<HolidayEntity> spec = Specs.all();

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        holidayTableFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();
      spec = spec.and(contains("name", a.name()));
      spec = spec.and(holidayDateFilter(a.holidayDate()));
      spec = spec.and(equalsTo("recurring", a.recurring()));
      spec = spec.and(inCodes("status", a.statusEnum(), StatusEnum::getCode));
    }

    return spec.and(orderByMonthDay());
  }

  /** Matches specific holidays by exact date OR recurring holidays by same month/day (ignoring year). */
  private Specification<HolidayEntity> holidayDateFilter(LocalDate date) {
    if (date == null) return alwaysTrue();
    return (root, query, cb) -> cb.or(
      cb.and(
        cb.equal(root.get("recurring"), false),
        cb.equal(root.get("holidayDate"), date)
      ),
      cb.and(
        cb.equal(root.get("recurring"), true),
        cb.equal(cb.function("MONTH", Integer.class, root.get("holidayDate")), date.getMonthValue()),
        cb.equal(cb.function("DAY", Integer.class, root.get("holidayDate")), date.getDayOfMonth())
      )
    );
  }

  /** Orders by calendar position (month, day) so recurring and specific holidays interleave naturally. */
  private Specification<HolidayEntity> orderByMonthDay() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        query.orderBy(
          cb.asc(cb.function("MONTH", Integer.class, root.get("holidayDate"))),
          cb.asc(cb.function("DAY", Integer.class, root.get("holidayDate"))),
          cb.desc(root.get("holidayDate"))
        );
      }
      return cb.conjunction();
    };
  }
}