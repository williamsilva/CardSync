package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.TransactionsAcqModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsAcqModel;
import com.cardsync.domain.filter.TransactionAcqSalesFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.TransactionAcqSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionAcqSalesService {

  private final TransactionAcqSpecs transactionAcqSpecs;
  private final TransactionTotalsQueryService totalsQueryService;
  private final TransactionAcqRepository transactionAcqRepository;
  private final TransactionsAcqModelAssembler transactionsAcqModelAssembler;

  @Transactional(readOnly = true)
  public Page<TransactionsAcqModel> search(Pageable pageable, ListQueryDto<TransactionAcqSalesFilter> query) {
    Specification<TransactionAcqEntity> spec = transactionAcqSpecs.fromQuery(query);

    Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

    return transactionAcqRepository
      .findAll(spec, unsortedPageable)
      .map(transactionsAcqModelAssembler::toModel);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<TransactionAcqSalesFilter> query) {
    Specification<TransactionAcqEntity> spec = transactionAcqSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      TransactionAcqEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue",
      "adjustment",
      "adjustmentValue"
    );
  }
}
