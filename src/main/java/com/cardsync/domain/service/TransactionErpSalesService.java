package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.TransactionsErpModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsErpModel;
import com.cardsync.domain.filter.TransactionErpSalesFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.repository.TransactionErpRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.TransactionErpSpecs;
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
public class TransactionErpSalesService {

  private final TransactionErpSpecs transactionErpSpecs;
  private final TransactionErpRepository transactionErpRepository;
  private final TransactionTotalsQueryService totalsQueryService;
  private final TransactionsErpModelAssembler transactionsErpModelAssembler;

  @Transactional(readOnly = true)
  public Page<TransactionsErpModel> search(Pageable pageable, ListQueryDto<TransactionErpSalesFilter> query) {
    Specification<TransactionErpEntity> filterSpec = transactionErpSpecs.fromQueryForTotals(query);
    Specification<TransactionErpEntity> dataSpec   = transactionErpSpecs.fromQuery(query);

    long total = transactionErpRepository.count(filterSpec);

    List<TransactionsErpModel> content = total == 0
      ? List.of()
      : transactionErpRepository.findAll(dataSpec, pageable)
      .stream()
      .map(transactionsErpModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<TransactionErpSalesFilter> query) {
    Specification<TransactionErpEntity> spec = transactionErpSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      TransactionErpEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue",
      "adjustment",
      "adjustmentValue"
    );
  }
}
