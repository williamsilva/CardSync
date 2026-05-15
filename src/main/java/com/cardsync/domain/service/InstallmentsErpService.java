package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.InstallmentsErpModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.InstallmentErpModel;
import com.cardsync.domain.filter.InstallmentsErpFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.repository.InstallmentErpRepository;
import com.cardsync.infrastructure.repository.spec.InstallmentsErpSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InstallmentsErpService {

  private final InstallmentsErpSpecs installmentsErpSpecs;
  private final InstallmentErpRepository installmentErpRepository;
  private final InstallmentsErpModelAssembler installmentErpModelAssembler;

  @Transactional(readOnly = true)
  public Page<InstallmentErpModel> search(Pageable pageable, ListQueryDto<InstallmentsErpFilter> query) {
    Specification<InstallmentErpEntity> spec = installmentsErpSpecs.fromQuery(query);

    Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

    return installmentErpRepository
      .findAll(spec, unsortedPageable)
      .map(installmentErpModelAssembler::toModel);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<InstallmentsErpFilter> query) {
    Specification<InstallmentErpEntity> spec = installmentsErpSpecs.fromQuery(query);

    var rows = installmentErpRepository.findAll(spec);

    BigDecimal totalGross = rows.stream()
      .map(InstallmentErpEntity::getGrossValue)
      .filter(Objects::nonNull)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalFee = rows.stream()
      .map(InstallmentErpEntity::getDiscountValue)
      .filter(Objects::nonNull)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalNet = rows.stream()
      .map(InstallmentErpEntity::getLiquidValue)
      .filter(Objects::nonNull)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalAdjustments = BigDecimal.ZERO;

    return new TransactionTotalsModel(totalGross, totalFee, totalNet, totalAdjustments, rows.size());
  }
}
