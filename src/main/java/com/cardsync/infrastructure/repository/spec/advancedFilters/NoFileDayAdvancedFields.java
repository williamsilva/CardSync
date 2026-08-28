package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.NoFileDayFilter;
import com.cardsync.domain.model.NoFileDayEntity;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.NoFileDayTypeEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class NoFileDayAdvancedFields extends BaseSpecificationSupport<NoFileDayEntity> {

  public NoFileDayAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<NoFileDayEntity> advanced(NoFileDayFilter filter) {
    if (filter == null) {
      return Specs.all();
    }

    Specification<NoFileDayEntity> spec = Specs.all();

    spec = spec.and(contains(filter.description(), "description"));
    spec = spec.and(inCodes("status", filter.statusEnum(), StatusEnum::getCode));
    spec = spec.and(inCodes("dayType", filter.dayType(), NoFileDayTypeEnum::getCode));
    // fileGroup é armazenado como nome do enum (String), não como código inteiro.
    spec = spec.and(inCodes("fileGroup", filter.fileGroup(), FileGroupEnum::name));
    spec = spec.and(noFileDateRange(filter.noFileDateFrom(), filter.noFileDateTo()));

    return spec;
  }

  /**
   * Painel manda um intervalo puro (sem período — ver NoFileDayAdvancedFilters), diferente do
   * padrão período+valor usado em outras telas; por isso não reaproveita localDatePeriod() aqui.
   */
  private Specification<NoFileDayEntity> noFileDateRange(String from, String to) {
    LocalDate start = parseDate(from);
    LocalDate end = parseDate(to);
    return localDateBetween("noFileDate", start, end, false);
  }
}
