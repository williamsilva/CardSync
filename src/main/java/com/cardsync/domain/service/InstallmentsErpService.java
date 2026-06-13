package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.InstallmentsErpModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.InstallmentErpModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.domain.filter.InstallmentsErpFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.repository.InstallmentErpRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.InstallmentsErpSpecs;
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
public class InstallmentsErpService {

  private final InstallmentsErpSpecs installmentsErpSpecs;
  private final InstallmentErpRepository installmentErpRepository;
  private final TransactionTotalsQueryService totalsQueryService;
  private final InstallmentsErpModelAssembler installmentErpModelAssembler;

  @Transactional(readOnly = true)
  public Page<InstallmentErpModel> search(Pageable pageable, ListQueryDto<InstallmentsErpFilter> query) {
    Specification<InstallmentErpEntity> filterSpec = installmentsErpSpecs.fromQueryForTotals(query);
    Specification<InstallmentErpEntity> dataSpec   = installmentsErpSpecs.fromQuery(query);

    long total = installmentErpRepository.count(filterSpec);

    List<InstallmentErpModel> content = total == 0
      ? List.of()
      : installmentErpRepository.findAll(dataSpec, pageable)
      .stream()
      .map(installmentErpModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<InstallmentsErpFilter> query) {
    Specification<InstallmentErpEntity> spec = installmentsErpSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      InstallmentErpEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue"
    );
  }
}
