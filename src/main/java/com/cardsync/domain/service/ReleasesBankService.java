package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.ReleasesBankModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.bank.ReleasesBankModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.ValueTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.domain.filter.ReleasesBankFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.repository.ReleasesBankRepository;
import com.cardsync.domain.service.support.ValueTotalsQueryService;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.ReleasesBankSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReleasesBankService {

  private final ReleasesBankSpecs releasesBankSpecs;
  private final ValueTotalsQueryService totalsQueryService;
  private final ReleasesBankRepository releasesBankRepository;
  private final ReleasesBankModelAssembler releasesBankModelAssembler;

  @Transactional(readOnly = true)
  public Page<ReleasesBankModel> search(Pageable pageable, ListQueryDto<ReleasesBankFilter> query) {
    Specification<ReleasesBankEntity> filterSpec = releasesBankSpecs.fromQueryForTotals(query);
    Specification<ReleasesBankEntity> dataSpec   = releasesBankSpecs.fromQuery(query);

    long total = releasesBankRepository.count(filterSpec);

    List<ReleasesBankModel> content = total == 0
      ? List.of()
      : releasesBankRepository.findAll(dataSpec, pageable)
      .stream()
      .map(releasesBankModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public ValueTotalsModel totals(ListQueryDto<ReleasesBankFilter> query) {
    Specification<ReleasesBankEntity> spec = releasesBankSpecs.fromQueryForTotals(query);
    return totalsQueryService.totals(ReleasesBankEntity.class, spec,"releaseValue");
  }

}