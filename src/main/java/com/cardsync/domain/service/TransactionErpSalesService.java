package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.TransactionsErpModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.erp.TransactionsErpModel;
import com.cardsync.bff.controller.v1.representation.model.erp.TransactionErpSalesTotalsModel;
import com.cardsync.domain.filter.TransactionErpSalesFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.repository.TransactionErpRepository;
import com.cardsync.infrastructure.repository.spec.TransactionErpSpecs;
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
public class TransactionErpSalesService {

  private final TransactionErpSpecs transactionErpSpecs;
  private final TransactionErpRepository transactionErpRepository;
  private final TransactionsErpModelAssembler transactionsErpModelAssembler;

  @Transactional(readOnly = true)
  public Page<TransactionsErpModel> search(Pageable pageable, ListQueryDto<TransactionErpSalesFilter> query) {
    Specification<TransactionErpEntity> spec = transactionErpSpecs.fromQuery(query);

    // A ordenação da tela ERP pode usar campos virtuais/com join, como expectedPaymentDate.
    // Por isso ela é aplicada dentro da Specification. O Pageable precisa seguir sem Sort,
    // senão o Spring Data tenta resolver expectedPaymentDate como atributo direto da entidade.
    Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

    return transactionErpRepository
      .findAll(spec, unsortedPageable)
      .map(transactionsErpModelAssembler::toModel);
  }

  @Transactional(readOnly = true)
  public TransactionErpSalesTotalsModel totals(ListQueryDto<TransactionErpSalesFilter> query) {
    Specification<TransactionErpEntity> spec = transactionErpSpecs.fromQuery(query);

    var rows = transactionErpRepository.findAll(spec);

    BigDecimal totalGross = rows.stream()
      .map(TransactionErpEntity::getGrossValue)
      .filter(Objects::nonNull)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalFee = rows.stream()
      .map(TransactionErpEntity::getDiscountValue)
      .filter(Objects::nonNull)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalNet = rows.stream()
      .map(TransactionErpEntity::getLiquidValue)
      .filter(Objects::nonNull)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalAdjustments = rows.stream()
      .map(TransactionErpEntity::getAdjustment)
      .filter(Objects::nonNull)
      .map(item -> item.getAdjustmentValue() == null ? BigDecimal.ZERO : item.getAdjustmentValue())
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new TransactionErpSalesTotalsModel(totalGross, totalFee, totalNet, totalAdjustments, rows.size());
  }
}
