package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.CreditOrderModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.CreditOrderModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.domain.filter.CreditOrderFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.CreditOrderSpecs;
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
public class CreditOrderService {

  private final CreditOrderSpecs creditOrderSpecs;
  private final CreditOrderRepository creditOrderRepository;
  private final TransactionTotalsQueryService totalsQueryService;
  private final CreditOrderModelAssembler transactionsAcqModelAssembler;

  @Transactional(readOnly = true)
  public Page<CreditOrderModel> search(Pageable pageable, ListQueryDto<CreditOrderFilter> query) {
    Specification<CreditOrderEntity> filterSpec = creditOrderSpecs.fromQueryForTotals(query);
    Specification<CreditOrderEntity> dataSpec   = creditOrderSpecs.fromQuery(query);

    long total = creditOrderRepository.count(filterSpec);

    List<CreditOrderModel> content = total == 0
      ? List.of()
      : creditOrderRepository.findAll(dataSpec, pageable)
      .stream()
      .map(transactionsAcqModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<CreditOrderFilter> query) {
    Specification<CreditOrderEntity> spec = creditOrderSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      CreditOrderEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue",
      "adjustment",
      "adjustmentValue"
    );
  }
}
