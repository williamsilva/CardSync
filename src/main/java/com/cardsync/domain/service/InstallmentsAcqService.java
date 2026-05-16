package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.InstallmentsAcqModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.InstallmentAcqModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.domain.filter.InstallmentsAcqFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.repository.InstallmentAcqRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.InstallmentsAcqSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstallmentsAcqService {

  private final InstallmentsAcqSpecs installmentsAcqSpecs;
  private final InstallmentAcqRepository installmentAcqRepository;
  private final TransactionTotalsQueryService totalsQueryService;
  private final InstallmentsAcqModelAssembler installmentAcqModelAssembler;

  @Transactional(readOnly = true)
  public Page<InstallmentAcqModel> search(Pageable pageable, ListQueryDto<InstallmentsAcqFilter> query) {
    Specification<InstallmentAcqEntity> spec = installmentsAcqSpecs.fromQuery(query);

    Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

    return installmentAcqRepository
      .findAll(spec, unsortedPageable)
      .map(installmentAcqModelAssembler::toModel);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<InstallmentsAcqFilter> query) {
    Specification<InstallmentAcqEntity> spec = installmentsAcqSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      InstallmentAcqEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue"
    );
  }
}
