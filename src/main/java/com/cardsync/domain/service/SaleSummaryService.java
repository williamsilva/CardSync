package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.SaleSummaryModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.SaleSummaryModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.domain.filter.SaleSummaryFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.SaleSummarySpecs;
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
public class SaleSummaryService {

  private final SaleSummarySpecs saleSummarySpecs;
  private final SalesSummaryRepository saleSummaryRepository;
  private final TransactionTotalsQueryService totalsQueryService;
  private final SaleSummaryModelAssembler transactionsAcqModelAssembler;

  @Transactional(readOnly = true)
  public Page<SaleSummaryModel> search(Pageable pageable, ListQueryDto<SaleSummaryFilter> query) {
    Specification<SalesSummaryEntity> filterSpec = saleSummarySpecs.fromQueryForTotals(query);
    Specification<SalesSummaryEntity> dataSpec   = saleSummarySpecs.fromQuery(query);

    long total = saleSummaryRepository.count(filterSpec);

    List<SaleSummaryModel> content = total == 0
      ? List.of()
      : saleSummaryRepository.findAll(dataSpec, pageable)
      .stream()
      .map(transactionsAcqModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<SaleSummaryFilter> query) {
    Specification<SalesSummaryEntity> spec = saleSummarySpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      SalesSummaryEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue",
      "adjustment",
      "adjustmentValue"
    );
  }
}
